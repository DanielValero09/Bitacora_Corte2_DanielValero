package com.restaurante.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.model.entity.UsuarioEntity;
import com.restaurante.repository.*;
import com.restaurante.service.*;
import com.restaurante.support.CatalogoTestFixture;
import com.restaurante.support.RelationalTestFixture;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration")
@AutoConfigureMockMvc
@Import(SecurityHttpTest.BusinessFixtures.class)
class SecurityHttpTest {
    @MockitoBean EventoRestauranteRepository eventos;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @Autowired PlatoService platos;
    @Autowired MesaService mesas;
    @Autowired CuentaService cuentas;
    @Autowired PedidoService pedidos;
    @Value("${jwt.secret}") String secret;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean IngredienteRepository ingredienteRepository;
    @MockitoBean PlatoRepository platoRepository;
    @MockitoBean MesaRepository mesaRepository;
    @MockitoBean CuentaRepository cuentaRepository;
    @MockitoBean PedidoRepository pedidoRepository;
    @MockitoBean PagoRepository pagoRepository;

    private static final String PASSWORD = "Password-exclusivo-test-10";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    private static final AtomicInteger SEQUENCE = new AtomicInteger(200);
    private final Map<String, UsuarioEntity> users = new HashMap<>();

    @BeforeEach
    void usuariosDePrueba() {
        for (RolUsuario rol : RolUsuario.values()) {
            String email = email(rol);
            users.put(email, UsuarioEntity.builder().email(email).password(HASH).rol(rol).activo(true).build());
        }
        when(usuarios.findByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(users.get(invocation.<String>getArgument(0).toLowerCase(Locale.ROOT))));
    }

    @Test
    void loginCorrectoDevuelveJwtConSubjectRolFechasYSinPassword() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email(RolUsuario.ROLE_GERENTE).toUpperCase(Locale.ROOT),
                                "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expirationMs").value(3600000))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();
        var claims = jwt.extractClaims(token);
        assertTrue(jwt.isValid(token));
        assertEquals(email(RolUsuario.ROLE_GERENTE), claims.getSubject());
        assertEquals("ROLE_GERENTE", claims.get("rol"));
        assertEquals(3600000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertFalse(body.contains(PASSWORD));
        assertFalse(body.contains(HASH));
        mvc.perform(get("/api/v1/platos").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void loginIncorrectoYUsuarioInexistenteNoSeDiferencian() throws Exception {
        String existing = loginFallido(email(RolUsuario.ROLE_GERENTE));
        String missing = loginFallido("inexistente@example.test");
        var one = json.readTree(existing);
        var two = json.readTree(missing);
        assertEquals(one.get("message"), two.get("message"));
        assertEquals(one.get("code"), two.get("code"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"invalido\",\"password\":\"p\"}",
            "{\"email\":\"a@example.test\",\"password\":\"\"}"})
    void loginValidaSolicitud(String body) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void usuarioInactivoNoPuedeHacerLoginNiUsarTokenAnterior() throws Exception {
        String token = token(RolUsuario.ROLE_MESERO);
        users.get(email(RolUsuario.ROLE_MESERO)).setActivo(false);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email(RolUsuario.ROLE_MESERO), "password", PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/mesas").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolCambiadoEnBdInvalidaTokenAnterior() throws Exception {
        String token = token(RolUsuario.ROLE_GERENTE);
        users.get(email(RolUsuario.ROLE_GERENTE)).setRol(RolUsuario.ROLE_CLIENTE);
        mvc.perform(get("/api/v1/platos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer basura", "Bearer ", "Basic dGVzdDp0ZXN0", "bearer basura"})
    void tokenBasuraOPrefijoIncorrectoProduce401Json(String authorization) throws Exception {
        mvc.perform(get("/api/v1/mesas").header("Authorization", authorization))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/mesas"))
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void tokenExpiradoFirmaIncorrectaYClaimsIncompletosProducen401() throws Exception {
        Instant now = Instant.now();
        var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        String expired = Jwts.builder().subject(email(RolUsuario.ROLE_GERENTE)).claim("rol", "ROLE_GERENTE")
                .issuedAt(Date.from(now.minusSeconds(120))).expiration(Date.from(now.minusSeconds(60))).signWith(key).compact();
        String wrong = new JwtUtil(Base64.getEncoder().encodeToString(new byte[32]), 3600000)
                .generateToken(email(RolUsuario.ROLE_GERENTE), RolUsuario.ROLE_GERENTE);
        String missingRole = Jwts.builder().subject(email(RolUsuario.ROLE_GERENTE)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(120))).signWith(key).compact();
        String missingExpiration = Jwts.builder().subject(email(RolUsuario.ROLE_GERENTE)).claim("rol", "ROLE_GERENTE")
                .issuedAt(Date.from(now)).signWith(key).compact();
        for (String token : List.of(expired, wrong, missingRole, missingExpiration)) {
            assertFalse(jwt.isValid(token));
            mvc.perform(get("/api/v1/platos").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void cadaEndpointProtegidoSinTokenProduce401(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.request()).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void clienteNoPuedeAccederANingunEndpointProtegido(Endpoint endpoint) throws Exception {
        mvc.perform(endpoint.request().header("Authorization", "Bearer " + token(RolUsuario.ROLE_CLIENTE)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void cartaPublicaSinTokenYConTokenInvalidoSigueAccesible() throws Exception {
        mvc.perform(get("/api/v1/carta")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/carta").header("Authorization", "Bearer basura")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/carta").header("Authorization", "Bearer " + token(RolUsuario.ROLE_CLIENTE)))
                .andExpect(status().isOk());
    }

    @Test
    void gerenteCreaActualizaYDesactivaCatalogo() throws Exception {
        String authorization = "Bearer " + token(RolUsuario.ROLE_GERENTE);
        String body = """
                {"nombre":"Catálogo %d","descripcion":"Prueba","precio":20,"combo":false,"ingredienteIds":[]}
                """.formatted(SEQUENCE.incrementAndGet());
        var created = mvc.perform(post("/api/v1/platos").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/v1/platos/{id}", id).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("20", "25")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precio").value(25));
        mvc.perform(delete("/api/v1/platos/{id}", id).header("Authorization", authorization)).andExpect(status().isNoContent());
    }

    @Test
    void meseroAbreCuentaCreaEditaConfirmaPedidoYCobra() throws Exception {
        long mesaId = mesas.crear(Mesa.builder().numero(SEQUENCE.incrementAndGet()).build()).getId();
        String authorization = "Bearer " + token(RolUsuario.ROLE_MESERO);
        long cuentaId = id(mvc.perform(post("/api/v1/mesas/{id}/cuentas", mesaId).header("Authorization", authorization))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long pedidoId = id(mvc.perform(post("/api/v1/cuentas/{id}/pedidos", cuentaId).header("Authorization", authorization))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long platoId = plato().getId();
        mvc.perform(post("/api/v1/pedidos/{id}/items", pedidoId).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platoId\":" + platoId + ",\"cantidad\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pedidos/{id}/confirmacion", pedidoId).header("Authorization", authorization))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/cuentas/{id}/pago", cuentaId).header("Authorization", authorization))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.monto").value(20));
    }

    @Test
    void cocineroPreparaMarcaListoYMeseroEntregaConRnIntactas() throws Exception {
        long pedidoId = pedidoConfirmado();
        mvc.perform(get("/api/v1/cocina/pedidos").header("Authorization", "Bearer " + token(RolUsuario.ROLE_COCINERO)))
                .andExpect(status().isOk());
        cambiarEstado(pedidoId, "EN_PREPARACION", RolUsuario.ROLE_MESERO, 403);
        cambiarEstado(pedidoId, "EN_PREPARACION", RolUsuario.ROLE_COCINERO, 200);
        mvc.perform(post("/api/v1/pedidos/{id}/items", pedidoId)
                        .header("Authorization", "Bearer " + token(RolUsuario.ROLE_MESERO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platoId\":" + plato().getId() + ",\"cantidad\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        cambiarEstado(pedidoId, "LISTO", RolUsuario.ROLE_COCINERO, 200);
        cambiarEstado(pedidoId, "ENTREGADO", RolUsuario.ROLE_COCINERO, 403);
        cambiarEstado(pedidoId, "ENTREGADO", RolUsuario.ROLE_MESERO, 200);
        assertEquals(3, pedidos.obtenerHistorial(pedidoId).size());
    }

    @Test
    void cocineroNoEvitaConfirmacionNiTransicionesValidas() throws Exception {
        long mesaId = mesas.crear(Mesa.builder().numero(SEQUENCE.incrementAndGet()).build()).getId();
        long pedidoId = pedidos.crear(cuentas.abrirCuenta(mesaId).getId()).getId();
        cambiarEstado(pedidoId, "EN_PREPARACION", RolUsuario.ROLE_COCINERO, 409);
        cambiarEstado(pedidoConfirmado(), "LISTO", RolUsuario.ROLE_COCINERO, 409);
    }

    @Test
    void rolesOperativosNoAdministranYMeseroNoAccedeATablero() throws Exception {
        for (RolUsuario rol : List.of(RolUsuario.ROLE_MESERO, RolUsuario.ROLE_COCINERO)) {
            mvc.perform(get("/api/v1/platos").header("Authorization", "Bearer " + token(rol))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/mesas").contentType(MediaType.APPLICATION_JSON).content("{\"numero\":9}")
                            .header("Authorization", "Bearer " + token(rol))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/cocina/pedidos").header("Authorization", "Bearer " + token(RolUsuario.ROLE_MESERO)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/cuentas").header("Authorization", "Bearer " + token(RolUsuario.ROLE_COCINERO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPermitePreflightExplicitoYRechazaOrigenExterno() throws Exception {
        for (String origin : List.of("http://localhost:3000", "http://localhost:5173")) {
            mvc.perform(options("/api/v1/platos").header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                    .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
        mvc.perform(options("/api/v1/platos").header("Origin", "https://externo.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void headersDeSeguridadSinSesionNiBasic() throws Exception {
        mvc.perform(get("/api/v1/carta"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void swaggerRecursosYOpenApiSonPublicosConSeguridadSoloEnOperacionesProtegidas() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/platos'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/carta'].get.security").doesNotExist());
    }

    private String loginFallido(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "incorrecta"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void passwordMultibyteFueraDeLimiteNoProduce500NiTruncamiento() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email(RolUsuario.ROLE_GERENTE), "password", "ñ".repeat(37)))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String email(RolUsuario rol) { return rol.name().toLowerCase(Locale.ROOT) + "@example.test"; }
    private String token(RolUsuario rol) { return jwt.generateToken(email(rol), rol); }
    private long id(String body) throws Exception { return json.readTree(body).get("id").asLong(); }
    private Plato plato() {
        return platos.crear(Plato.builder().nombre("Plato " + SEQUENCE.incrementAndGet()).precio(new BigDecimal("20"))
                .combo(false).build(), List.of());
    }
    private long pedidoConfirmado() {
        long mesaId = mesas.crear(Mesa.builder().numero(SEQUENCE.incrementAndGet()).build()).getId();
        long pedidoId = pedidos.crear(cuentas.abrirCuenta(mesaId).getId()).getId();
        pedidos.agregarItem(pedidoId, plato().getId(), 1);
        pedidos.confirmar(pedidoId);
        return pedidoId;
    }
    private void cambiarEstado(long pedidoId, String estado, RolUsuario rol, int status) throws Exception {
        mvc.perform(patch("/api/v1/pedidos/{id}/estado", pedidoId).header("Authorization", "Bearer " + token(rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"" + estado + "\",\"usuarioResponsable\":\"operador-test\"}"))
                .andExpect(status().is(status));
    }

    static Stream<Endpoint> protectedEndpoints() {
        String plato = "{\"nombre\":\"Plato\",\"precio\":20,\"combo\":false,\"ingredienteIds\":[]}";
        return Stream.of(
                new Endpoint("POST", "/api/v1/ingredientes", "{\"nombre\":\"Queso\",\"disponible\":true}"),
                new Endpoint("GET", "/api/v1/ingredientes", null),
                new Endpoint("GET", "/api/v1/ingredientes/1", null),
                new Endpoint("PATCH", "/api/v1/ingredientes/1/disponibilidad", "{\"disponible\":false}"),
                new Endpoint("POST", "/api/v1/platos", plato),
                new Endpoint("GET", "/api/v1/platos", null),
                new Endpoint("GET", "/api/v1/platos/1", null),
                new Endpoint("PUT", "/api/v1/platos/1", plato),
                new Endpoint("DELETE", "/api/v1/platos/1", null),
                new Endpoint("POST", "/api/v1/mesas", "{\"numero\":1}"),
                new Endpoint("GET", "/api/v1/mesas", null),
                new Endpoint("GET", "/api/v1/mesas/1", null),
                new Endpoint("POST", "/api/v1/mesas/1/cuentas", null),
                new Endpoint("GET", "/api/v1/mesas/1/cuenta-abierta", null),
                new Endpoint("GET", "/api/v1/cuentas", null),
                new Endpoint("GET", "/api/v1/cuentas/1", null),
                new Endpoint("POST", "/api/v1/cuentas/1/pedidos", null),
                new Endpoint("GET", "/api/v1/cuentas/1/pedidos", null),
                new Endpoint("GET", "/api/v1/pedidos", null),
                new Endpoint("GET", "/api/v1/pedidos/1", null),
                new Endpoint("POST", "/api/v1/pedidos/1/items", "{\"platoId\":1,\"cantidad\":1}"),
                new Endpoint("PATCH", "/api/v1/pedidos/1/items/1", "{\"cantidad\":2}"),
                new Endpoint("DELETE", "/api/v1/pedidos/1/items/1", null),
                new Endpoint("PATCH", "/api/v1/pedidos/1/items/1/bebida", null),
                new Endpoint("POST", "/api/v1/pedidos/1/confirmacion", null),
                new Endpoint("PATCH", "/api/v1/pedidos/1/estado", "{\"nuevoEstado\":\"LISTO\",\"usuarioResponsable\":\"test\"}"),
                new Endpoint("GET", "/api/v1/pedidos/1/historial", null),
                new Endpoint("GET", "/api/v1/cocina/pedidos", null),
                new Endpoint("POST", "/api/v1/cuentas/1/pago", null),
                new Endpoint("GET", "/api/v1/cuentas/1/pago", null),
                new Endpoint("GET", "/api/v1/pagos", null),
                new Endpoint("GET", "/api/v1/pagos/1", null));
    }

    record Endpoint(String method, String path, String body) {
        MockHttpServletRequestBuilder request() {
            var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path);
            return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
    }

    @TestConfiguration
    static class BusinessFixtures {
        @Bean CatalogoTestFixture securityCatalogo() { return new CatalogoTestFixture(); }
        @Bean RelationalTestFixture securityRelational(CatalogoTestFixture catalogo) { return new RelationalTestFixture(catalogo.platos()); }
        @Bean @Primary PlatoService securityPlatos(CatalogoTestFixture fixture) { return fixture.platos(); }
        @Bean @Primary IngredienteService securityIngredientes(CatalogoTestFixture fixture) { return fixture.ingredientes(); }
        @Bean @Primary MesaService securityMesas(RelationalTestFixture fixture) { return fixture.mesas(); }
        @Bean @Primary CuentaService securityCuentas(RelationalTestFixture fixture) { return fixture.cuentas(); }
        @Bean @Primary PedidoService securityPedidos(RelationalTestFixture fixture) { return fixture.pedidos(); }
        @Bean @Primary PagoService securityPagos(RelationalTestFixture fixture) { return fixture.pagos(); }
    }
}
