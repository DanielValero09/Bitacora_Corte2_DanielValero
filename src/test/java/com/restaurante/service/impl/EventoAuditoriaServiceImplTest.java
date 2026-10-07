package com.restaurante.service.impl;

import com.restaurante.mapper.EventoRestauranteMapper;
import com.restaurante.model.document.EventoRestauranteDocument;
import com.restaurante.model.domain.EventoRestaurante;
import com.restaurante.repository.EventoRestauranteRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventoAuditoriaServiceImplTest {
    private final EventoRestauranteRepository repository = mock(EventoRestauranteRepository.class);
    private final EventoAuditoriaServiceImpl service = new EventoAuditoriaServiceImpl(
            repository, Mappers.getMapper(EventoRestauranteMapper.class));

    @Test
    void consultaDerivadaOrdenadaConservaOrdenEIdsAlMapear() {
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 6, 10, 0);
        var primero = EventoRestauranteDocument.builder().id("primero")
                .entidadTipo("Pedido").entidadId(7L).timestamp(fecha).build();
        var segundo = EventoRestauranteDocument.builder().id("segundo")
                .entidadTipo("Pedido").entidadId(7L).timestamp(fecha.plusMinutes(1)).build();
        when(repository.findByEntidadTipoAndEntidadIdOrderByTimestampAsc("Pedido", 7L))
                .thenReturn(List.of(primero, segundo));

        List<EventoRestaurante> resultado = service.listarPorEntidad("Pedido", 7L);

        assertEquals(List.of("primero", "segundo"), resultado.stream()
                .map(EventoRestaurante::getId).toList());
        assertEquals(List.of(fecha, fecha.plusMinutes(1)), resultado.stream()
                .map(EventoRestaurante::getTimestamp).toList());
        verify(repository).findByEntidadTipoAndEntidadIdOrderByTimestampAsc("Pedido", 7L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void entidadSinEventosDevuelveListaVacia() {
        when(repository.findByEntidadTipoAndEntidadIdOrderByTimestampAsc("Pedido", 99L))
                .thenReturn(List.of());

        assertTrue(service.listarPorEntidad("Pedido", 99L).isEmpty());
    }
}
