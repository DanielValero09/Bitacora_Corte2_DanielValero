package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.MesaEntityMapper;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.entity.MesaEntity;
import com.restaurante.repository.MesaRepository;
import com.restaurante.service.MesaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MesaServiceImpl implements MesaService {
    private final MesaRepository repository;
    private final MesaEntityMapper mapper;

    @Override
    @Transactional
    public Mesa crear(Mesa mesa) {
        if (repository.existsByNumero(mesa.getNumero())) {
            throw duplicada(mesa.getNumero());
        }
        MesaEntity entity = mapper.toEntity(mesa);
        entity.setId(null);
        try {
            Mesa creada = mapper.toDomain(repository.saveAndFlush(entity));
            log.info("Mesa creada: id={}, numero={}", creada.getId(), creada.getNumero());
            return creada;
        } catch (DataIntegrityViolationException exception) {
            throw duplicada(mesa.getNumero());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Mesa obtenerPorId(Long id) {
        return mapper.toDomain(buscarEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mesa> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private MesaEntity buscarEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> {
            log.warn("Mesa inexistente: id={}", id);
            return new ResourceNotFoundException("No existe la mesa con id: " + id);
        });
    }

    private ResourceAlreadyExistsException duplicada(Integer numero) {
        log.warn("Intento de crear mesa con numero duplicado: {}", numero);
        return new ResourceAlreadyExistsException("Ya existe una mesa con numero: " + numero);
    }
}
