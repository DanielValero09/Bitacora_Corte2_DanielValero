package com.restaurante.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @CsvSource({
            "not-found, 404, RESOURCE_NOT_FOUND, Recurso inexistente",
            "already-exists, 409, RESOURCE_ALREADY_EXISTS, Recurso duplicado",
            "business-rule, 409, BUSINESS_RULE_VIOLATION, Regla incumplida",
            "invalid-state, 409, INVALID_ORDER_STATE, Estado inválido"
    })
    void erroresDeNegocioRespetanElContrato(String endpoint, int statusCode, String code, String message)
            throws Exception {
        String path = "/test/errors/" + endpoint;
        assertContract(mockMvc.perform(get(path).queryParam("dato", "privado")),
                statusCode, code, message, path)
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void validacionDevuelveLosCamposInvalidos() throws Exception {
        assertContract(mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \",\"precio\":0}")),
                400, "VALIDATION_ERROR", "La solicitud contiene campos inválidos", "/test/errors/validation")
                .andExpect(jsonPath("$.fieldErrors.nombre").value("no debe estar vacío"))
                .andExpect(jsonPath("$.fieldErrors.precio").value("debe ser mayor que 0"));
    }

    @Test
    void variosErroresDelMismoCampoSeleccionanElMenorMensajeAlfabetico() throws Exception {
        assertContract(mockMvc.perform(post("/test/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":1}")),
                400, "VALIDATION_ERROR", "La solicitud contiene campos inválidos", "/test/errors/validation")
                .andExpect(jsonPath("$.fieldErrors.nombre").value("debe tener al menos 3 caracteres"))
                .andExpect(jsonPath("$.fieldErrors.precio").doesNotExist());
    }

    @Test
    void errorInesperadoOcultaDetallesInternos() throws Exception {
        String body = assertContract(mockMvc.perform(get("/test/errors/unexpected")),
                500, "INTERNAL_ERROR", "Ocurrió un error interno del servidor", "/test/errors/unexpected")
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("credencial-secreta"));
        assertFalse(body.contains("RuntimeException"));
        assertFalse(body.contains("TestController"));
    }

    private ResultActions assertContract(ResultActions result, int statusCode, String code,
                                         String message, String path) throws Exception {
        result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.fieldErrors").isMap());
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
        assertEquals(6, body.size());
        assertDoesNotThrow(() -> LocalDateTime.parse(body.get("timestamp").asText()));
        return result;
    }

    @RestController
    @RequestMapping("/test/errors")
    public static class TestController {

        @GetMapping("/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("Recurso inexistente");
        }

        @GetMapping("/already-exists")
        public void alreadyExists() {
            throw new ResourceAlreadyExistsException("Recurso duplicado");
        }

        @GetMapping("/business-rule")
        public void businessRule() {
            throw new BusinessRuleException("Regla incumplida");
        }

        @GetMapping("/invalid-state")
        public void invalidState() {
            throw new InvalidOrderStateException("Estado inválido");
        }

        @PostMapping("/validation")
        public void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new RuntimeException("credencial-secreta");
        }
    }

    public record TestRequest(
            @NotBlank(message = "no debe estar vacío")
            @Size(min = 3, message = "debe tener al menos 3 caracteres") String nombre,
            @Positive(message = "debe ser mayor que 0") int precio) {
    }
}
