package com.restaurante.service;

import com.restaurante.model.domain.EventoRestaurante;

import java.util.List;

public interface EventoAuditoriaService {
    List<EventoRestaurante> listarPorEntidad(String entidadTipo, Long entidadId);
}
