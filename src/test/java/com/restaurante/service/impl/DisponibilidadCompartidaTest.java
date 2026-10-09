package com.restaurante.service.impl;

import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import com.restaurante.support.CatalogoTestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisponibilidadCompartidaTest {
    @Test
    void cambiarIngredienteAfectaTodosSusPlatosAlConsultarlosNuevamente() {
        CatalogoTestFixture catalogo = new CatalogoTestFixture();
        IngredienteServiceImpl ingredientes = catalogo.ingredientes();
        PlatoServiceImpl platos = catalogo.platos();
        Ingrediente queso = ingredientes.crear(
                Ingrediente.builder().nombre("Queso").disponible(true).build());
        Plato hamburguesa = platos.crear(Plato.builder().nombre("Hamburguesa").build(), List.of(queso.getId()));
        Plato papas = platos.crear(Plato.builder().nombre("Papas").build(), List.of(queso.getId()));

        assertEquals(queso.getId(), hamburguesa.getIngredientes().getFirst().getId());
        assertEquals(queso.getId(), papas.getIngredientes().getFirst().getId());
        assertTrue(hamburguesa.isDisponible());
        assertTrue(papas.isDisponible());

        ingredientes.cambiarDisponibilidad(queso.getId(), false);
        Plato hamburguesaAgotada = platos.obtenerPorId(hamburguesa.getId());
        Plato papasAgotadas = platos.obtenerPorId(papas.getId());
        assertFalse(hamburguesaAgotada.isDisponible());
        assertFalse(papasAgotadas.isDisponible());
        assertEquals(List.of(hamburguesa.getId(), papas.getId()),
                platos.listarCarta().stream().map(Plato::getId).toList());

        ingredientes.cambiarDisponibilidad(queso.getId(), true);
        assertTrue(platos.obtenerPorId(hamburguesa.getId()).isDisponible());
        assertTrue(platos.obtenerPorId(papas.getId()).isDisponible());
    }
}
