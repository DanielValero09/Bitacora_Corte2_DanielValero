package com.restaurante.service;

import com.restaurante.model.domain.Ingrediente;
import java.util.List;

public interface IngredienteService {
    Ingrediente crear(Ingrediente ingrediente);
    Ingrediente obtenerPorId(Long id);
    List<Ingrediente> listar();
    Ingrediente cambiarDisponibilidad(Long id, boolean disponible);
    List<Ingrediente> obtenerPorIds(List<Long> ids);
}
