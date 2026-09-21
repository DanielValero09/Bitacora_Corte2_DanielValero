package com.restaurante.service;

import com.restaurante.model.domain.Mesa;

import java.util.List;

public interface MesaService {
    Mesa crear(Mesa mesa);

    Mesa obtenerPorId(Long id);

    List<Mesa> listar();
}
