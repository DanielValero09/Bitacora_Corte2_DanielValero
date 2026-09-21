package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import com.restaurante.service.IngredienteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatoServiceImplTest {
    @Mock
    private IngredienteService ingredienteService;
    @InjectMocks
    private PlatoServiceImpl service;

    @Test
    void crearGeneraIdYConservaCamposEditables() {
        Plato entrada = nuevo(" Hamburguesa ");
        entrada.setId(999L);
        entrada.setCombo(true);
        Plato creado = service.crear(entrada, List.of());
        assertEquals(1L, creado.getId());
        assertEquals("Hamburguesa", creado.getNombre());
        assertEquals("Descripcion", creado.getDescripcion());
        assertEquals(new BigDecimal("15000"), creado.getPrecio());
        assertTrue(creado.isCombo());
        assertSame(creado, service.obtenerPorId(creado.getId()));
    }

    @Test
    void crearSiempreIniciaActivo() {
        Plato entrada = nuevo("Hamburguesa");
        entrada.setActivo(false);
        assertTrue(service.crear(entrada, List.of()).isActivo());
    }

    @Test
    void crearResuelveIngredientesYConservaReferencias() {
        Ingrediente queso = Ingrediente.builder().id(7L).disponible(true).build();
        when(ingredienteService.obtenerPorIds(List.of(7L))).thenReturn(List.of(queso));
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of(7L));
        assertSame(queso, plato.getIngredientes().getFirst());
        verify(ingredienteService).obtenerPorIds(List.of(7L));
    }

    @Test
    void ingredienteInexistenteSePropagaSinGuardarPlato() {
        when(ingredienteService.obtenerPorIds(List.of(99L)))
                .thenThrow(new ResourceNotFoundException("Ingrediente inexistente"));
        assertThrows(ResourceNotFoundException.class, () -> service.crear(nuevo("Hamburguesa"), List.of(99L)));
        assertTrue(service.listarTodos().isEmpty());
    }

    @Test
    void rechazaNombreDuplicadoIgnorandoMayusculasYEspacios() {
        service.crear(nuevo("Hamburguesa"), List.of());
        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.crear(nuevo(" hamburguesa "), List.of()));
    }

    @Test
    void obtenerExistente() {
        Plato creado = service.crear(nuevo("Hamburguesa"), List.of());
        assertSame(creado, service.obtenerPorId(creado.getId()));
    }

    @Test
    void obtenerInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarTodosVacio() {
        assertTrue(service.listarTodos().isEmpty());
    }

    @Test
    void listarTodosIncluyeActivosEInactivosConIdsDistintos() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(primero.getId());
        assertNotEquals(primero.getId(), segundo.getId());
        assertEquals(List.of(primero, segundo), service.listarTodos());
    }

    @Test
    void cartaExcluyeInactivos() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(primero.getId());
        assertEquals(List.of(segundo), service.listarCarta());
    }

    @Test
    void cartaIncluyeActivoNoDisponible() {
        Ingrediente agotado = Ingrediente.builder().id(7L).disponible(false).build();
        when(ingredienteService.obtenerPorIds(List.of(7L))).thenReturn(List.of(agotado));
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of(7L));
        assertFalse(plato.isDisponible());
        assertEquals(List.of(plato), service.listarCarta());
    }

    @Test
    void actualizarReemplazaTodosLosCamposEditablesYReferencias() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        Ingrediente nuevoIngrediente = Ingrediente.builder().id(7L).disponible(false).build();
        when(ingredienteService.obtenerPorIds(List.of(7L))).thenReturn(List.of(nuevoIngrediente));
        Plato cambios = nuevo(" Hamburguesa especial ");
        cambios.setDescripcion("");
        cambios.setPrecio(new BigDecimal("20000"));
        cambios.setCombo(true);
        Plato actualizado = service.actualizar(original.getId(), cambios, List.of(7L));
        assertSame(original, actualizado);
        assertEquals("Hamburguesa especial", actualizado.getNombre());
        assertEquals("", actualizado.getDescripcion());
        assertEquals(new BigDecimal("20000"), actualizado.getPrecio());
        assertTrue(actualizado.isCombo());
        assertSame(nuevoIngrediente, actualizado.getIngredientes().getFirst());
        assertFalse(actualizado.isDisponible());
    }

    @Test
    void actualizarConservaIdIgnorandoIdDeCambios() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        Long id = original.getId();
        Plato cambios = nuevo("Papas");
        cambios.setId(999L);
        assertEquals(id, service.actualizar(id, cambios, List.of()).getId());
    }

    @Test
    void actualizarConservaActivoTrue() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        assertTrue(service.actualizar(original.getId(), nuevo("Papas"), List.of()).isActivo());
    }

    @Test
    void actualizarConservaActivoFalse() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        service.desactivar(original.getId());
        Plato cambios = nuevo("Papas");
        cambios.setActivo(true);
        assertFalse(service.actualizar(original.getId(), cambios, List.of()).isActivo());
    }

    @Test
    void actualizarRechazaNombreDeOtroPlatoInclusoInactivo() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(segundo.getId());
        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.actualizar(primero.getId(), nuevo(" PAPAS "), List.of()));
        assertEquals("Hamburguesa", primero.getNombre());
    }

    @Test
    void actualizarPermiteConservarSuPropioNombre() {
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of());
        assertEquals("HAMBURGUESA",
                service.actualizar(plato.getId(), nuevo(" HAMBURGUESA "), List.of()).getNombre());
    }

    @Test
    void actualizarInexistenteFallaAntesDeResolverIngredientes() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizar(99L, nuevo("Papas"), List.of(7L)));
        verifyNoInteractions(ingredienteService);
    }

    @Test
    void actualizarConIngredienteInexistenteNoDejaCambiosParciales() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        when(ingredienteService.obtenerPorIds(List.of(99L)))
                .thenThrow(new ResourceNotFoundException("Ingrediente inexistente"));
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizar(original.getId(), nuevo("Papas"), List.of(99L)));
        assertEquals("Hamburguesa", original.getNombre());
        assertTrue(original.getIngredientes().isEmpty());
    }

    @Test
    void desactivarEsLogicoEIdempotente() {
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of());
        service.desactivar(plato.getId());
        service.desactivar(plato.getId());
        assertFalse(plato.isActivo());
        assertSame(plato, service.obtenerPorId(plato.getId()));
        assertEquals(List.of(plato), service.listarTodos());
    }

    @Test
    void desactivarInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.desactivar(99L));
    }

    private Plato nuevo(String nombre) {
        return Plato.builder().nombre(nombre).descripcion("Descripcion")
                .precio(new BigDecimal("15000")).build();
    }
}
