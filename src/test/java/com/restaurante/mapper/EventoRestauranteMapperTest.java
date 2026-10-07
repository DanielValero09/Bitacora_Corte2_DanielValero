package com.restaurante.mapper;

import com.restaurante.model.document.EventoRestauranteDocument;
import com.restaurante.model.domain.EventoRestaurante;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventoRestauranteMapperTest {
    private final EventoRestauranteMapper mapper = Mappers.getMapper(EventoRestauranteMapper.class);
    private final LocalDateTime fecha = LocalDateTime.of(2026, 10, 6, 12, 30);
    private final Map<String, Object> metadatos = Map.of(
            "estadoAnterior", "RECIBIDO", "estadoNuevo", "EN_PREPARACION");

    @Test
    void dominioADocumentoConservaDatosEIgnoraIdParaCreacion() {
        EventoRestaurante evento = EventoRestaurante.builder()
                .id("no-reutilizar")
                .tipo("CAMBIO_ESTADO_PEDIDO").entidadTipo("Pedido").entidadId(7L)
                .descripcion("Pedido cambió de RECIBIDO a EN_PREPARACION")
                .usuario("cocinero").timestamp(fecha).metadatos(metadatos).build();

        EventoRestauranteDocument document = mapper.toDocument(evento);

        assertNull(document.getId());
        assertEquals(evento.getTipo(), document.getTipo());
        assertEquals(evento.getEntidadTipo(), document.getEntidadTipo());
        assertEquals(evento.getEntidadId(), document.getEntidadId());
        assertEquals(evento.getDescripcion(), document.getDescripcion());
        assertEquals(evento.getUsuario(), document.getUsuario());
        assertEquals(evento.getTimestamp(), document.getTimestamp());
        assertEquals(evento.getMetadatos(), document.getMetadatos());
        assertNotSame(evento.getMetadatos(), document.getMetadatos());
    }

    @Test
    void documentoADominioRecuperaTodosLosCamposIncluidoIdMongo() {
        EventoRestauranteDocument document = EventoRestauranteDocument.builder()
                .id("mongo-id")
                .tipo("CAMBIO_ESTADO_PEDIDO").entidadTipo("Pedido").entidadId(7L)
                .descripcion("Pedido cambió de RECIBIDO a EN_PREPARACION")
                .usuario("cocinero").timestamp(fecha).metadatos(metadatos).build();

        EventoRestaurante evento = mapper.toDomain(document);

        assertEquals(document.getId(), evento.getId());
        assertEquals(document.getTipo(), evento.getTipo());
        assertEquals(document.getEntidadTipo(), evento.getEntidadTipo());
        assertEquals(document.getEntidadId(), evento.getEntidadId());
        assertEquals(document.getDescripcion(), evento.getDescripcion());
        assertEquals(document.getUsuario(), evento.getUsuario());
        assertEquals(document.getTimestamp(), evento.getTimestamp());
        assertEquals(document.getMetadatos(), evento.getMetadatos());
        assertNotSame(document.getMetadatos(), evento.getMetadatos());
    }
}
