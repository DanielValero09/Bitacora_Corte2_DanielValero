package com.restaurante.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.controller.CartaController;
import com.restaurante.controller.CocinaController;
import com.restaurante.controller.CuentaController;
import com.restaurante.controller.IngredienteController;
import com.restaurante.controller.MesaController;
import com.restaurante.controller.PagoController;
import com.restaurante.controller.PedidoController;
import com.restaurante.controller.PlatoController;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.mapper.CambioEstadoPedidoMapper;
import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.IngredienteMapper;
import com.restaurante.mapper.ItemPedidoMapper;
import com.restaurante.mapper.MesaMapper;
import com.restaurante.mapper.PagoMapper;
import com.restaurante.mapper.PedidoMapper;
import com.restaurante.mapper.PlatoMapper;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.IngredienteServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PagoServiceImpl;
import com.restaurante.service.impl.PedidoServiceImpl;
import com.restaurante.service.impl.PlatoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AmericanBitesHttpIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        IngredienteServiceImpl ingredientes = new IngredienteServiceImpl();
        PlatoServiceImpl platos = new PlatoServiceImpl(ingredientes);
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        PedidoServiceImpl pedidos = new PedidoServiceImpl(cuentas, platos);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);

        IngredienteMapper ingredienteMapper = Mappers.getMapper(IngredienteMapper.class);
        PlatoMapper platoMapper = Mappers.getMapper(PlatoMapper.class);
        ReflectionTestUtils.setField(platoMapper, "ingredienteMapper", ingredienteMapper);
        MesaMapper mesaMapper = Mappers.getMapper(MesaMapper.class);
        CuentaMapper cuentaMapper = Mappers.getMapper(CuentaMapper.class);
        ItemPedidoMapper itemMapper = Mappers.getMapper(ItemPedidoMapper.class);
        PedidoMapper pedidoMapper = Mappers.getMapper(PedidoMapper.class);
        ReflectionTestUtils.setField(pedidoMapper, "itemPedidoMapper", itemMapper);
        CambioEstadoPedidoMapper cambioMapper = Mappers.getMapper(CambioEstadoPedidoMapper.class);
        PagoMapper pagoMapper = Mappers.getMapper(PagoMapper.class);

        mvc = MockMvcBuilders.standaloneSetup(
                        new IngredienteController(ingredientes, ingredienteMapper),
                        new PlatoController(platos, platoMapper),
                        new CartaController(platos, platoMapper),
                        new MesaController(mesas, mesaMapper, cuentas, cuentaMapper),
                        new CuentaController(cuentas, cuentaMapper),
                        new PedidoController(pedidos, pedidoMapper, cambioMapper),
                        new CocinaController(pedidos, pedidoMapper),
                        new PagoController(pagos, pagoMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void flujoHttpPrincipalCreaConfirmaPasaPorCocinaYPaga() throws Exception {
        long ingredienteId = idDe(postJson("/api/v1/ingredientes",
                "{\"nombre\":\"Carne\",\"disponible\":true}"));
        long platoId = idDe(postJson("/api/v1/platos", """
                {"nombre":"Combo American","descripcion":"Con bebida","precio":28.00,
                 "combo":true,"ingredienteIds":[%d]}
                """.formatted(ingredienteId)));
        long mesaId = idDe(postJson("/api/v1/mesas", "{\"numero\":20}"));
        long cuentaId = idDe(postSinBody("/api/v1/mesas/{id}/cuentas", mesaId));
        long pedidoId = idDe(postSinBody("/api/v1/cuentas/{id}/pedidos", cuentaId));

        mvc.perform(post("/api/v1/pedidos/{id}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":" + platoId + ",\"cantidad\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bebidaIncluida").value(true))
                .andExpect(jsonPath("$.total").value(28.00));
        mvc.perform(post("/api/v1/pedidos/{id}/confirmacion", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmado").value(true));
        mvc.perform(get("/api/v1/cocina/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pedidoId));

        cambiarEstado(pedidoId, "EN_PREPARACION", "cocinero-1");
        cambiarEstado(pedidoId, "LISTO", "cocinero-2");
        cambiarEstado(pedidoId, "ENTREGADO", "mesero-1");

        mvc.perform(get("/api/v1/cocina/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/v1/cuentas/{id}/pago", cuentaId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monto").value(28.00));
        mvc.perform(get("/api/v1/cuentas/{id}", cuentaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.total").value(28.00));
    }

    @Test
    void disponibilidadCompartidaSeReflejaEnCartaYRechazaAgregarConHttp409() throws Exception {
        long ingredienteId = idDe(postJson("/api/v1/ingredientes",
                "{\"nombre\":\"Queso\",\"disponible\":true}"));
        long platoId = idDe(postJson("/api/v1/platos", """
                {"nombre":"Hamburguesa","descripcion":"Con queso","precio":20.00,
                 "combo":false,"ingredienteIds":[%d]}
                """.formatted(ingredienteId)));
        long mesaId = idDe(postJson("/api/v1/mesas", "{\"numero\":21}"));
        long cuentaId = idDe(postSinBody("/api/v1/mesas/{id}/cuentas", mesaId));
        long pedidoId = idDe(postSinBody("/api/v1/cuentas/{id}/pedidos", cuentaId));

        mvc.perform(patch("/api/v1/ingredientes/{id}/disponibilidad", ingredienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disponible\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/carta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(platoId))
                .andExpect(jsonPath("$[0].disponible").value(false));
        mvc.perform(post("/api/v1/pedidos/{id}/items", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platoId\":" + platoId + ",\"cantidad\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    private String postJson(String ruta, String body) throws Exception {
        return mvc.perform(post(ruta)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String postSinBody(String ruta, long id) throws Exception {
        return mvc.perform(post(ruta, id))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private long idDe(String body) throws Exception {
        JsonNode respuesta = json.readTree(body);
        return respuesta.get("id").asLong();
    }

    private void cambiarEstado(long pedidoId, String estado, String usuario) throws Exception {
        mvc.perform(patch("/api/v1/pedidos/{id}/estado", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"" + estado
                                + "\",\"usuarioResponsable\":\"" + usuario + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(estado));
    }
}
