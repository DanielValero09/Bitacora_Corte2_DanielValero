package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.IngredienteMapper;
import com.restaurante.mapper.PlatoMapper;
import com.restaurante.service.impl.IngredienteServiceImpl;
import com.restaurante.service.impl.PlatoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CartaHttpTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PLATO = """
            {"nombre":"Hamburguesa","descripcion":"Con queso","precio":15000,
             "combo":false,"ingredienteIds":[]}
            """;

    @BeforeEach
    void setUp() {
        IngredienteServiceImpl ingredientes = new IngredienteServiceImpl();
        PlatoServiceImpl platos = new PlatoServiceImpl(ingredientes);
        IngredienteMapper ingredienteMapper = Mappers.getMapper(IngredienteMapper.class);
        PlatoMapper platoMapper = Mappers.getMapper(PlatoMapper.class);
        ReflectionTestUtils.setField(platoMapper, "ingredienteMapper", ingredienteMapper);
        mvc = MockMvcBuilders.standaloneSetup(
                        new IngredienteController(ingredientes, ingredienteMapper),
                        new PlatoController(platos, platoMapper),
                        new CartaController(platos, platoMapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void crearIngredienteDevuelve201YPermiteConsultarYListar() throws Exception {
        long id = crearIngrediente();
        mvc.perform(get("/api/v1/ingredientes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Queso"))
                .andExpect(jsonPath("$.disponible").value(true));
        mvc.perform(get("/api/v1/ingredientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"nombre\":\" \",\"disponible\":true}",
            "{\"nombre\":\"Queso\",\"disponible\":null}"
    })
    void ingredienteInvalidoDevuelve400(String body) throws Exception {
        mvc.perform(post("/api/v1/ingredientes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/ingredientes")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void ingredienteDuplicadoDevuelve409() throws Exception {
        crearIngrediente();
        mvc.perform(post("/api/v1/ingredientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" queso \",\"disponible\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void patchActualizaDisponibilidadYValidaNotNull() throws Exception {
        long id = crearIngrediente();
        mvc.perform(patch("/api/v1/ingredientes/{id}/disponibilidad", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.disponible").value(false));
        mvc.perform(patch("/api/v1/ingredientes/{id}/disponibilidad", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearPlatoDevuelve201YPermiteConsultarYListar() throws Exception {
        long id = crearPlato(PLATO);
        mvc.perform(get("/api/v1/platos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Hamburguesa"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.combo").value(false))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.ingredientes").isEmpty());
        mvc.perform(get("/api/v1/platos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"nombre\":\"\",\"precio\":1,\"combo\":false,\"ingredienteIds\":[]}",
            "{\"nombre\":\"Papas\",\"precio\":0,\"combo\":false,\"ingredienteIds\":[]}",
            "{\"nombre\":\"Papas\",\"precio\":1,\"combo\":null,\"ingredienteIds\":[]}",
            "{\"nombre\":\"Papas\",\"precio\":1,\"combo\":false,\"ingredienteIds\":null}",
            "{\"nombre\":\"Papas\",\"precio\":1,\"combo\":false,\"ingredienteIds\":[0]}",
            "{\"nombre\":\"Papas\",\"precio\":1,\"combo\":false,\"ingredienteIds\":[null]}"
    })
    void platoInvalidoDevuelve400EnPostYPut(String body) throws Exception {
        mvc.perform(post("/api/v1/platos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/v1/platos/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/platos")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void limitesDeLongitudSeValidan() throws Exception {
        String nombreLargo = "x".repeat(101);
        mvc.perform(post("/api/v1/ingredientes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nombre", nombreLargo, "disponible", true))))
                .andExpect(status().isBadRequest());
        var entrada = json.readTree(PLATO);
        ((com.fasterxml.jackson.databind.node.ObjectNode) entrada).put("nombre", nombreLargo);
        ((com.fasterxml.jackson.databind.node.ObjectNode) entrada).put("descripcion", "x".repeat(1001));
        mvc.perform(post("/api/v1/platos").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(entrada)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists())
                .andExpect(jsonPath("$.fieldErrors.descripcion").exists());
    }

    @Test
    void platoDuplicadoDevuelve409() throws Exception {
        crearPlato(PLATO);
        mvc.perform(post("/api/v1/platos").contentType(MediaType.APPLICATION_JSON).content(PLATO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void recursosInexistentesDevuelven404() throws Exception {
        mvc.perform(get("/api/v1/ingredientes/99")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/platos/99")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/platos/99")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/platos/99").contentType(MediaType.APPLICATION_JSON).content(PLATO))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/ingredientes/99/disponibilidad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/platos").contentType(MediaType.APPLICATION_JSON)
                        .content(PLATO.replace("[]", "[99]")))
                .andExpect(status().isNotFound());
    }

    @Test
    void putDevuelveCamposActualizados() throws Exception {
        long id = crearPlato(PLATO);
        mvc.perform(put("/api/v1/platos/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Papas","descripcion":"","precio":0.01,
                                 "combo":true,"ingredienteIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Papas"))
                .andExpect(jsonPath("$.descripcion").value(""))
                .andExpect(jsonPath("$.precio").value(0.01))
                .andExpect(jsonPath("$.combo").value(true))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void deleteDevuelve204YConservaPlatoAdministrativo() throws Exception {
        long id = crearPlato(PLATO);
        mvc.perform(delete("/api/v1/platos/{id}", id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/platos/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mvc.perform(get("/api/v1/platos"))
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/v1/carta"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void cartaExponeDisponibilidadActualTrasPatchDeIngrediente() throws Exception {
        long ingredienteId = crearIngrediente();
        long platoId = crearPlato(PLATO.replace("[]", "[" + ingredienteId + "]"));
        mvc.perform(get("/api/v1/carta")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disponible").value(true));
        mvc.perform(patch("/api/v1/ingredientes/{id}/disponibilidad", ingredienteId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/carta")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(platoId))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].disponible").value(false))
                .andExpect(jsonPath("$[0].ingredientes[0].id").value(ingredienteId))
                .andExpect(jsonPath("$[0].ingredientes[0].disponible").value(false));
    }

    private long crearIngrediente() throws Exception {
        String body = mvc.perform(post("/api/v1/ingredientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Queso\",\"disponible\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long crearPlato(String entrada) throws Exception {
        String body = mvc.perform(post("/api/v1/platos").contentType(MediaType.APPLICATION_JSON).content(entrada))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
