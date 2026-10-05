package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.PagoMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Plato;
import com.restaurante.service.PlatoService;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PagoServiceImpl;
import com.restaurante.service.impl.PedidoServiceImpl;
import com.restaurante.support.RelationalTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import com.restaurante.support.SecurityHttpTestSupport;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagoHttpTest {
    private MockMvc mvc;

    @org.junit.jupiter.api.AfterEach
    void cerrarContextoSeguridad() {
        SecurityHttpTestSupport.close(mvc);
    }
    private final ObjectMapper json = new ObjectMapper();
    private CuentaServiceImpl cuentas;
    private PagoServiceImpl pagos;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        PlatoService platos = mock(PlatoService.class);
        Plato plato = Plato.builder().id(1L).nombre("Hamburguesa")
                .precio(new BigDecimal("20.00")).activo(true).build();
        when(platos.obtenerPorId(1L)).thenReturn(plato);
        RelationalTestFixture persistence = new RelationalTestFixture(platos);
        MesaServiceImpl mesas = persistence.mesas();
        cuentas = persistence.cuentas();
        PedidoServiceImpl pedidos = persistence.pedidos();
        pagos = persistence.pagos();
        CuentaMapper cuentaMapper = Mappers.getMapper(CuentaMapper.class);
        PagoMapper pagoMapper = Mappers.getMapper(PagoMapper.class);
        mvc = SecurityHttpTestSupport.securedSetup(
                        new PagoController(pagos, pagoMapper),
                        new CuentaController(cuentas, cuentaMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Mesa mesa = mesas.crear(Mesa.builder().numero(20).build());
        cuenta = cuentas.abrirCuenta(mesa.getId());
        pedidos.agregarItem(pedidos.crear(cuenta.getId()).getId(), plato.getId(), 1);
    }

    @Test
    void registrarPagoDevuelve201YLasConsultasReflejanPagoYCuentaCerrada() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuentaId").value(cuenta.getId()))
                .andExpect(jsonPath("$.monto").value(20.00))
                .andExpect(jsonPath("$.fechaHora").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long pagoId = json.readTree(respuesta).get("id").asLong();

        mvc.perform(get("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pagoId));
        mvc.perform(get("/api/v1/pagos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pagoId));
        mvc.perform(get("/api/v1/pagos/{id}", pagoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuentaId").value(cuenta.getId()));
        mvc.perform(get("/api/v1/cuentas/{id}", cuenta.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.fechaCierre").isNotEmpty())
                .andExpect(jsonPath("$.total").value(20.00));
    }

    @Test
    void registrarPagoParaCuentaInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/v1/cuentas/99/pago"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void registrarPagoParaCuentaCerradaDevuelve409() throws Exception {
        pagos.registrarPago(cuenta.getId());

        mvc.perform(post("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void segundoPagoDevuelve409() throws Exception {
        mvc.perform(post("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void consultarCuentaSinPagoDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/cuentas/{cuentaId}/pago", cuenta.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void consultarPagoInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/pagos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
