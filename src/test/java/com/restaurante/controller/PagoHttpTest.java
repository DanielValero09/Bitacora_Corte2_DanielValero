package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.PagoMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PagoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PagoHttpTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private CuentaServiceImpl cuentas;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        cuentas = new CuentaServiceImpl(mesas);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);
        CuentaMapper cuentaMapper = Mappers.getMapper(CuentaMapper.class);
        PagoMapper pagoMapper = Mappers.getMapper(PagoMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new PagoController(pagos, pagoMapper),
                        new CuentaController(cuentas, cuentaMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Mesa mesa = mesas.crear(Mesa.builder().numero(20).build());
        cuenta = cuentas.abrirCuenta(mesa.getId());
        ItemPedido item = ItemPedido.builder()
                .id(1L)
                .platoId(1L)
                .nombrePlato("Hamburguesa")
                .precioCongelado(new BigDecimal("20.00"))
                .cantidad(1)
                .build();
        Pedido pedido = Pedido.builder()
                .id(1L)
                .cuentaId(cuenta.getId())
                .items(new ArrayList<>(List.of(item)))
                .build();
        cuenta.getPedidos().add(pedido);
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
        cuenta.setEstado(EstadoCuenta.CERRADA);

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
