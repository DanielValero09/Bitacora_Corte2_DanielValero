package com.restaurante.service;

import com.restaurante.model.domain.Plato;
import java.util.List;

public interface PlatoService {
    Plato crear(Plato plato, List<Long> ingredienteIds);
    Plato obtenerPorId(Long id);
    List<Plato> listarTodos();
    List<Plato> listarCarta();
    Plato actualizar(Long id, Plato cambios, List<Long> ingredienteIds);
    void desactivar(Long id);
}
