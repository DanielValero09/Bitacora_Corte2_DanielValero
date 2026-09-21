package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Mesa;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesaServiceImplTest {
    private final MesaServiceImpl service = new MesaServiceImpl();

    @Test
    void crearGeneraIdYConservaNumero() {
        Mesa entrada = Mesa.builder().id(99L).numero(10).build();

        Mesa creada = service.crear(entrada);

        assertEquals(1L, creada.getId());
        assertEquals(10, creada.getNumero());
    }

    @Test
    void idsSonDiferentesEIncrementales() {
        assertEquals(1L, service.crear(nueva(10)).getId());
        assertEquals(2L, service.crear(nueva(20)).getId());
    }

    @Test
    void numeroDuplicadoLanzaExcepcion() {
        service.crear(nueva(10));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.crear(nueva(10)));
        assertEquals(1, service.listar().size());
    }

    @Test
    void obtenerMesaExistente() {
        Mesa creada = service.crear(nueva(10));

        assertSame(creada, service.obtenerPorId(creada.getId()));
    }

    @Test
    void mesaInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarContieneElementosOrdenadosPorId() {
        Mesa primera = service.crear(nueva(20));
        Mesa segunda = service.crear(nueva(10));

        assertEquals(List.of(primera, segunda), service.listar());
    }

    private Mesa nueva(int numero) {
        return Mesa.builder().numero(numero).build();
    }
}
