package com.restaurante.auditoria;

import com.restaurante.mapper.EventoRestauranteMapper;
import com.restaurante.mapper.EventoRestauranteMapperImpl;
import com.restaurante.model.document.EventoRestauranteDocument;
import com.restaurante.model.domain.EventoRestaurante;
import com.restaurante.repository.EventoRestauranteRepository;
import com.restaurante.service.EventoAuditoriaService;
import com.restaurante.service.impl.EventoAuditoriaServiceImpl;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mongo real exclusivamente opt-in: mvn -Dtest=S09MongoAuditIT -Ds09.mongo=true test.
 * Usa MONGODB_URI (localhost por defecto). No requiere PostgreSQL ni secretos JWT.
 */
@EnabledIfSystemProperty(named = "s09.mongo", matches = "true")
@DataMongoTest(properties = "spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/american_bites}")
@Import({EventoRestauranteMapperImpl.class, EventoAuditoriaServiceImpl.class})
class S09MongoAuditIT {
    @Autowired private MongoDatabaseFactory databaseFactory;
    @Autowired private EventoRestauranteRepository repository;
    @Autowired private EventoRestauranteMapper mapper;
    @Autowired private EventoAuditoriaService service;

    @Test
    void conexionGuardadoRecuperacionQueryYOrdenConLimpiezaExclusiva() {
        Document ping = databaseFactory.getMongoDatabase().runCommand(new Document("ping", 1));
        assertEquals(1.0, ((Number) ping.get("ok")).doubleValue());
        Long pedidoId = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String usuario = "s09-mongo-it-" + UUID.randomUUID();
        // BSON almacena milisegundos: usar precisión representable para comparar exactamente.
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 6, 12, 0);
        List<EventoRestauranteDocument> creados = new ArrayList<>();
        try {
            // Insertar fuera de orden para validar que Mongo ordena, no el fixture.
            var segundo = evento(pedidoId, usuario, fecha.plusMinutes(1), "EN_PREPARACION", "LISTO");
            var primero = evento(pedidoId, usuario, fecha, "RECIBIDO", "EN_PREPARACION");
            var otraEntidad = evento(pedidoId, usuario, fecha, "RECIBIDO", "EN_PREPARACION");
            otraEntidad.setEntidadTipo("Mesa");
            for (EventoRestaurante evento : List.of(segundo, primero, otraEntidad)) {
                EventoRestauranteDocument document = mapper.toDocument(evento);
                creados.add(document);
                repository.save(document);
                assertNotNull(document.getId());
                assertFalse(document.getId().isBlank());
            }

            var recuperado = repository.findById(creados.get(1).getId()).orElseThrow();
            assertEquals(creados.get(1).getId(), recuperado.getId());
            assertEquals("CAMBIO_ESTADO_PEDIDO", recuperado.getTipo());
            assertEquals("Pedido", recuperado.getEntidadTipo());
            assertEquals(pedidoId, recuperado.getEntidadId());
            assertEquals("Pedido cambió de RECIBIDO a EN_PREPARACION", recuperado.getDescripcion());
            assertEquals(usuario, recuperado.getUsuario());
            assertEquals(fecha, recuperado.getTimestamp());
            assertEquals(Map.of("estadoAnterior", "RECIBIDO", "estadoNuevo", "EN_PREPARACION"),
                    recuperado.getMetadatos());

            List<EventoRestaurante> encontrados = service.listarPorEntidad("Pedido", pedidoId);
            assertEquals(List.of(creados.get(1).getId(), creados.get(0).getId()),
                    encontrados.stream().map(EventoRestaurante::getId).toList());
            assertEquals(List.of(fecha, fecha.plusMinutes(1)),
                    encontrados.stream().map(EventoRestaurante::getTimestamp).toList());
            assertTrue(encontrados.stream().allMatch(e -> usuario.equals(e.getUsuario())));
        } finally {
            // Nunca deleteAll() ni borrar por un filtro amplio: solo IDs de esta ejecución.
            for (EventoRestauranteDocument creado : creados) {
                if (creado.getId() != null) {
                    repository.deleteById(creado.getId());
                }
            }
        }
        assertTrue(repository.findByEntidadTipoAndEntidadIdOrderByTimestampAsc("Pedido", pedidoId).isEmpty());
        assertTrue(repository.findByEntidadTipoAndEntidadIdOrderByTimestampAsc("Mesa", pedidoId).isEmpty());
    }

    private EventoRestaurante evento(Long pedidoId, String usuario, LocalDateTime fecha,
                                     String anterior, String nuevo) {
        return EventoRestaurante.builder()
                .tipo("CAMBIO_ESTADO_PEDIDO").entidadTipo("Pedido").entidadId(pedidoId)
                .descripcion("Pedido cambió de " + anterior + " a " + nuevo)
                .usuario(usuario).timestamp(fecha)
                .metadatos(Map.of("estadoAnterior", anterior, "estadoNuevo", nuevo)).build();
    }
}
