package com.restaurante.service.impl;

import com.restaurante.mapper.EventoRestauranteMapper;
import com.restaurante.model.domain.EventoRestaurante;
import com.restaurante.repository.EventoRestauranteRepository;
import com.restaurante.service.EventoAuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventoAuditoriaServiceImpl implements EventoAuditoriaService {
    private final EventoRestauranteRepository repository;
    private final EventoRestauranteMapper mapper;

    @Override
    public List<EventoRestaurante> listarPorEntidad(String entidadTipo, Long entidadId) {
        return repository.findByEntidadTipoAndEntidadIdOrderByTimestampAsc(entidadTipo, entidadId)
                .stream().map(mapper::toDomain).toList();
    }
}
