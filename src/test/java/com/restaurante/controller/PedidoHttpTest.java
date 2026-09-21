package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.CambioEstadoPedidoMapper;
import com.restaurante.mapper.ItemPedidoMapper;
import com.restaurante.mapper.PedidoMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.IngredienteServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PedidoServiceImpl;
import com.restaurante.service.impl.PlatoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PedidoHttpTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private CuentaServiceImpl cuentas;
    private PlatoServiceImpl platos;
    private Cuenta cuenta;
    private Plato plato;

    @Test
    void estadoDesconocidoDevuelve400SinModificarPedido() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        mvc.perform(patch("/api/v1/pedidos/{id}/estado", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"INEXISTENTE\",\"usuarioResponsable\":\"cocina\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId))
                .andExpect(jsonPath("$.estado").value("RECIBIDO"));
        mvc.perform(get("/api/v1/pedidos/{id}/historial", pedidoId))
                .andExpect(jsonPath("$").isEmpty());
    }

    @BeforeEach
    void setUp() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        cuentas = new CuentaServiceImpl(mesas);
        IngredienteServiceImpl ingredientes = new IngredienteServiceImpl();
        platos = new PlatoServiceImpl(ingredientes);
        PedidoServiceImpl pedidos = new PedidoServiceImpl(cuentas, platos);
        PedidoMapper pedidoMapper = Mappers.getMapper(PedidoMapper.class);
        ReflectionTestUtils.setField(pedidoMapper, "itemPedidoMapper",
                Mappers.getMapper(ItemPedidoMapper.class));
        CambioEstadoPedidoMapper cambioMapper = Mappers.getMapper(CambioEstadoPedidoMapper.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new PedidoController(pedidos, pedidoMapper, cambioMapper),
                        new CocinaController(pedidos, pedidoMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Mesa mesa = mesas.crear(Mesa.builder().numero(1).build());
        cuenta = cuentas.abrirCuenta(mesa.getId());
        plato = platos.crear(Plato.builder()
                .nombre("Combo hamburguesa")
                .descripcion("Con bebida")
                .precio(new BigDecimal("20000"))
                .combo(true)
                .build(), List.of());
    }

    @Test
    void crearPedidoDevuelve201YPermiteConsultarYListar() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedidoId))
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.confirmado").value(false))
                .andExpect(jsonPath("$.fechaConfirmacion").doesNotExist())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/pedidos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(pedidoId));
        mvc.perform(get("/api/v1/cuentas/{cuentaId}/pedidos", cuenta.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(pedidoId));
    }

    @Test
    void crearEnCuentaInexistenteDevuelve404() throws Exception {
        mvc.perform(post("/api/v1/cuentas/99/pedidos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void crearEnCuentaCerradaDevuelve409() throws Exception {
        cuenta.setEstado(EstadoCuenta.CERRADA);

        mvc.perform(post("/api/v1/cuentas/{cuentaId}/pedidos", cuenta.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void agregarItemDevuelvePedidoActualizado() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(post("/api/v1/pedidos/{pedidoId}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":" + plato.getId() + ",\"cantidad\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nombrePlato").value("Combo hamburguesa"))
                .andExpect(jsonPath("$.items[0].precioCongelado").value(20000))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].bebidaIncluida").value(true))
                .andExpect(jsonPath("$.items[0].subtotal").value(40000))
                .andExpect(jsonPath("$.total").value(40000));
    }

    @Test
    void inputInvalidoAlAgregarDevuelve400() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(post("/api/v1/pedidos/{pedidoId}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":0,\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void platoInexistenteDevuelve404() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(post("/api/v1/pedidos/{pedidoId}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":99,\"cantidad\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void platoAgotadoDevuelve409() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        plato.setIngredientes(List.of(Ingrediente.builder().disponible(false).build()));

        mvc.perform(post("/api/v1/pedidos/{pedidoId}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":" + plato.getId() + ",\"cantidad\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void actualizarCantidadYRetirarBebidaDevuelven200() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        long itemId = agregarItem(pedidoId);

        mvc.perform(patch("/api/v1/pedidos/{pedidoId}/items/{itemId}", pedidoId, itemId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(3));
        mvc.perform(patch("/api/v1/pedidos/{pedidoId}/items/{itemId}/bebida", pedidoId, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bebidaIncluida").value(false))
                .andExpect(jsonPath("$.items[0].subtotal").value(60000));
    }

    @Test
    void eliminarItemDevuelve204() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        long itemId = agregarItem(pedidoId);

        mvc.perform(delete("/api/v1/pedidos/{pedidoId}/items/{itemId}", pedidoId, itemId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/pedidos/{id}", pedidoId))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void pedidoInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/pedidos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void confirmarPedidoValidoDevuelve200ConEstadoRecibidoYFecha() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        agregarItem(pedidoId);

        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmado").value(true))
                .andExpect(jsonPath("$.fechaConfirmacion").exists())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"));
    }

    @Test
    void confirmarPedidoVacioYConfirmarDosVecesDevuelven409() throws Exception {
        long vacioId = crearPedido(cuenta.getId());
        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", vacioId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        long pedidoId = crearPedido(cuenta.getId());
        agregarItem(pedidoId);
        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", pedidoId))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", pedidoId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void tableroCocinaDevuelvePedidoConfirmado() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        agregarItem(pedidoId);
        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", pedidoId))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/cocina/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pedidoId))
                .andExpect(jsonPath("$[0].confirmado").value(true))
                .andExpect(jsonPath("$[0].estado").value("RECIBIDO"));
    }

    @Test
    void cambiarEstadoValidoDevuelve200YSeConsultaEnHistorial() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());
        agregarItem(pedidoId);
        mvc.perform(post("/api/v1/pedidos/{pedidoId}/confirmacion", pedidoId))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/pedidos/{pedidoId}/estado", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_PREPARACION\","
                                + "\"usuarioResponsable\":\"cocinero-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));

        mvc.perform(get("/api/v1/pedidos/{pedidoId}/historial", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoAnterior").value("RECIBIDO"))
                .andExpect(jsonPath("$[0].estadoNuevo").value("EN_PREPARACION"))
                .andExpect(jsonPath("$[0].usuarioResponsable").value("cocinero-1"))
                .andExpect(jsonPath("$[0].fechaHora").exists());
    }

    @Test
    void bodyInvalidoAlCambiarEstadoDevuelve400() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(patch("/api/v1/pedidos/{pedidoId}/estado", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":null,\"usuarioResponsable\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.nuevoEstado").exists())
                .andExpect(jsonPath("$.fieldErrors.usuarioResponsable").exists());
    }

    @Test
    void transicionInvalidaDevuelve409YPedidoInexistente404() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(patch("/api/v1/pedidos/{pedidoId}/estado", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"LISTO\","
                                + "\"usuarioResponsable\":\"cocinero\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));

        mvc.perform(patch("/api/v1/pedidos/99/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"EN_PREPARACION\","
                                + "\"usuarioResponsable\":\"cocinero\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void historialSinCambiosDevuelveListaVaciaYPedidoInexistente404() throws Exception {
        long pedidoId = crearPedido(cuenta.getId());

        mvc.perform(get("/api/v1/pedidos/{pedidoId}/historial", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/pedidos/99/historial"))
                .andExpect(status().isNotFound());
    }

    private long crearPedido(long cuentaId) throws Exception {
        String body = mvc.perform(post("/api/v1/cuentas/{cuentaId}/pedidos", cuentaId))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long agregarItem(long pedidoId) throws Exception {
        String body = mvc.perform(post("/api/v1/pedidos/{pedidoId}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":" + plato.getId() + ",\"cantidad\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("items").get(0).get("id").asLong();
    }
}
