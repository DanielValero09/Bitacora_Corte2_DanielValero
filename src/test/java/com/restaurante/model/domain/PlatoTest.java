package com.restaurante.model.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatoTest {

    @Test
    void platoActivoConTodosLosIngredientesDisponiblesEstaDisponible() {
        Plato plato = Plato.builder()
                .activo(true)
                .ingredientes(List.of(
                        Ingrediente.builder().nombre("Pan").disponible(true).build(),
                        Ingrediente.builder().nombre("Carne").disponible(true).build()))
                .build();

        assertTrue(plato.isDisponible());
    }

    @Test
    void platoActivoConUnIngredienteAgotadoNoEstaDisponible() {
        Plato plato = Plato.builder()
                .activo(true)
                .ingredientes(List.of(
                        Ingrediente.builder().nombre("Pan").disponible(true).build(),
                        Ingrediente.builder().nombre("Carne").disponible(false).build()))
                .build();

        assertFalse(plato.isDisponible());
    }

    @Test
    void platoInactivoNoEstaDisponibleAunqueSusIngredientesLoEsten() {
        Plato plato = Plato.builder()
                .activo(false)
                .ingredientes(List.of(Ingrediente.builder().disponible(true).build()))
                .build();

        assertFalse(plato.isDisponible());
    }

    @Test
    void platoActivoSinIngredientesEstaDisponible() {
        Plato plato = Plato.builder().activo(true).build();

        assertTrue(plato.isDisponible());
    }
}
