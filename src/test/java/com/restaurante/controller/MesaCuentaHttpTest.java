package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.MesaMapper;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MesaCuentaHttpTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        MesaMapper mesaMapper = Mappers.getMapper(MesaMapper.class);
        CuentaMapper cuentaMapper = Mappers.getMapper(CuentaMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new MesaController(mesas, mesaMapper, cuentas, cuentaMapper),
                        new CuentaController(cuentas, cuentaMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void crearMesaDevuelve201YPermiteConsultarYListar() throws Exception {
        long mesaId = crearMesa(10);

        mvc.perform(get("/api/v1/mesas/{id}", mesaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mesaId))
                .andExpect(jsonPath("$.numero").value(10));
        mvc.perform(get("/api/v1/mesas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mesaId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"numero\":0}", "{\"numero\":-1}"})
    void mesaConInputInvalidoDevuelve400(String body) throws Exception {
        mvc.perform(post("/api/v1/mesas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/mesas")).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void mesaDuplicadaDevuelve409() throws Exception {
        crearMesa(10);

        mvc.perform(post("/api/v1/mesas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void mesaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/mesas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void abrirCuentaDevuelve201YPermiteConsultarlaYListarla() throws Exception {
        long mesaId = crearMesa(10);
        long cuentaId = abrirCuenta(mesaId);

        mvc.perform(get("/api/v1/cuentas/{id}", cuentaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cuentaId))
                .andExpect(jsonPath("$.mesaId").value(mesaId))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.fechaApertura").isNotEmpty())
                .andExpect(jsonPath("$.fechaCierre").doesNotExist())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/cuentas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cuentaId));
    }

    @Test
    void segundaCuentaAbiertaDevuelve409() throws Exception {
        long mesaId = crearMesa(10);
        abrirCuenta(mesaId);

        mvc.perform(post("/api/v1/mesas/{id}/cuentas", mesaId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void abrirCuentaEnMesaInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/v1/mesas/99/cuentas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cuentaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/cuentas/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void consultarCuentaAbiertaDevuelve200() throws Exception {
        long mesaId = crearMesa(10);
        long cuentaId = abrirCuenta(mesaId);

        mvc.perform(get("/api/v1/mesas/{id}/cuenta-abierta", mesaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cuentaId));
    }

    @Test
    void consultarCuentaAbiertaDevuelve404SiLaMesaNoTieneUna() throws Exception {
        long mesaId = crearMesa(10);

        mvc.perform(get("/api/v1/mesas/{id}/cuenta-abierta", mesaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "La mesa con id " + mesaId + " no tiene una cuenta abierta"));
    }

    @Test
    void consultarCuentaAbiertaDevuelve404SiLaMesaNoExiste() throws Exception {
        mvc.perform(get("/api/v1/mesas/99/cuenta-abierta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private long crearMesa(int numero) throws Exception {
        String body = mvc.perform(post("/api/v1/mesas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":" + numero + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long abrirCuenta(long mesaId) throws Exception {
        String body = mvc.perform(post("/api/v1/mesas/{id}/cuentas", mesaId))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }
}
