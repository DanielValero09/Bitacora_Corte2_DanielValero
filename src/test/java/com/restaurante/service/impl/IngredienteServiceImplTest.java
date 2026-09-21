package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Ingrediente;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngredienteServiceImplTest {
    private final IngredienteServiceImpl service = new IngredienteServiceImpl();

    @Test
    void crearGeneraIdYConservaDatos() {
        Ingrediente ingrediente = nuevo(" Queso ");
        ingrediente.setId(999L);
        Ingrediente creado = service.crear(ingrediente);
        assertEquals(1L, creado.getId());
        assertEquals("Queso", creado.getNombre());
        assertTrue(creado.isDisponible());
        assertSame(creado, service.obtenerPorId(creado.getId()));
    }

    @Test
    void idsSonIncrementales() {
        assertEquals(1L, service.crear(nuevo("Queso")).getId());
        assertEquals(2L, service.crear(nuevo("Pan")).getId());
    }

    @Test
    void rechazaDuplicadoIgnorandoMayusculasYEspacios() {
        service.crear(nuevo("Queso"));
        assertThrows(ResourceAlreadyExistsException.class, () -> service.crear(nuevo(" queso ")));
        assertEquals(1, service.listar().size());
    }

    @Test
    void obtieneIngredienteExistente() {
        Ingrediente creado = service.crear(nuevo("Pan"));
        assertSame(creado, service.obtenerPorId(creado.getId()));
    }

    @Test
    void obtenerInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarContieneTodosEnOrdenDeId() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        Ingrediente pan = service.crear(nuevo("Pan"));
        assertEquals(List.of(queso, pan), service.listar());
    }

    @Test
    void cambiarDisponibilidadModificaMismaInstanciaEnAmbosSentidos() {
        Ingrediente creado = service.crear(nuevo("Queso"));
        assertSame(creado, service.cambiarDisponibilidad(creado.getId(), false));
        assertFalse(creado.isDisponible());
        assertSame(creado, service.cambiarDisponibilidad(creado.getId(), true));
        assertTrue(creado.isDisponible());
    }

    @Test
    void cambiarDisponibilidadInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.cambiarDisponibilidad(99L, false));
    }

    @Test
    void obtenerPorIdsMantieneOrdenYReferencias() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        Ingrediente pan = service.crear(nuevo("Pan"));
        List<Ingrediente> resultado = service.obtenerPorIds(List.of(pan.getId(), queso.getId()));
        assertSame(pan, resultado.get(0));
        assertSame(queso, resultado.get(1));
    }

    @Test
    void obtenerPorIdsConInexistenteLanzaExcepcion() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerPorIds(List.of(queso.getId(), 99L)));
    }

    @Test
    void obtenerPorIdsVacioPermitePlatosSinIngredientes() {
        assertTrue(service.obtenerPorIds(List.of()).isEmpty());
    }

    private Ingrediente nuevo(String nombre) {
        return Ingrediente.builder().nombre(nombre).disponible(true).build();
    }
}
