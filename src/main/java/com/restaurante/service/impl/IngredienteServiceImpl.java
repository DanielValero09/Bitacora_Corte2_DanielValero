package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.IngredienteEntityMapper;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.entity.IngredienteEntity;
import com.restaurante.repository.IngredienteRepository;
import com.restaurante.service.IngredienteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngredienteServiceImpl implements IngredienteService {
    private final IngredienteRepository repository;
    private final IngredienteEntityMapper mapper;

    @Override
    @Transactional
    public Ingrediente crear(Ingrediente ingrediente) {
        String nombre = ingrediente.getNombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            log.warn("Intento de crear ingrediente duplicado: {}", nombre);
            throw new ResourceAlreadyExistsException("Ya existe un ingrediente con nombre: " + nombre);
        }

        IngredienteEntity entity = mapper.toEntity(ingrediente);
        entity.setId(null);
        entity.setNombre(nombre);
        Ingrediente creado = mapper.toDomain(repository.save(entity));
        log.info("Ingrediente creado: id={}", creado.getId());
        return creado;
    }

    @Override
    @Transactional(readOnly = true)
    public Ingrediente obtenerPorId(Long id) {
        return mapper.toDomain(buscarEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingrediente> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Ingrediente cambiarDisponibilidad(Long id, boolean disponible) {
        IngredienteEntity ingrediente = buscarEntity(id);
        ingrediente.setDisponible(disponible);
        Ingrediente actualizado = mapper.toDomain(repository.save(ingrediente));
        log.info("Disponibilidad de ingrediente modificada: id={}, disponible={}", id, disponible);
        return actualizado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingrediente> obtenerPorIds(List<Long> ids) {
        return ids.stream()
                .map(this::buscarEntity)
                .map(mapper::toDomain)
                .toList();
    }

    private IngredienteEntity buscarEntity(Long id) {
        return repository.findById(id).orElseThrow(() -> {
            log.warn("Ingrediente inexistente: id={}", id);
            return new ResourceNotFoundException("No existe el ingrediente con id: " + id);
        });
    }
}
