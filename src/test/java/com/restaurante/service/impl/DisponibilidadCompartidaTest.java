package com.restaurante.service.impl;

import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisponibilidadCompartidaTest {
    @Test
    void cambiarIngredienteAfectaTodosSusPlatosSinModificarlos() {
        IngredienteServiceImpl ingredientes = new IngredienteServiceImpl();
        PlatoServiceImpl platos = new PlatoServiceImpl(ingredientes);
        Ingrediente queso = ingredientes.crear(
                Ingrediente.builder().nombre("Queso").disponible(true).build());
        Plato hamburguesa = platos.crear(Plato.builder().nombre("Hamburguesa").build(), List.of(queso.getId()));
        Plato papas = platos.crear(Plato.builder().nombre("Papas").build(), List.of(queso.getId()));

        assertSame(queso, hamburguesa.getIngredientes().getFirst());
        assertSame(queso, papas.getIngredientes().getFirst());
        assertTrue(hamburguesa.isDisponible());
        assertTrue(papas.isDisponible());

        ingredientes.cambiarDisponibilidad(queso.getId(), false);
        assertFalse(hamburguesa.isDisponible());
        assertFalse(papas.isDisponible());
        assertEquals(List.of(hamburguesa, papas), platos.listarCarta());

        ingredientes.cambiarDisponibilidad(queso.getId(), true);
        assertTrue(hamburguesa.isDisponible());
        assertTrue(papas.isDisponible());
    }
}
