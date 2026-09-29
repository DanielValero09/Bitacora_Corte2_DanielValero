package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.PlatoEntityMapper;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.entity.IngredienteEntity;
import com.restaurante.model.entity.PlatoEntity;
import com.restaurante.repository.IngredienteRepository;
import com.restaurante.repository.PlatoRepository;
import com.restaurante.service.PlatoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatoServiceImpl implements PlatoService {
    private final PlatoRepository platoRepository;
    private final IngredienteRepository ingredienteRepository;
    private final PlatoEntityMapper mapper;

    @Override
    @Transactional
    public Plato crear(Plato plato, List<Long> ingredienteIds) {
        String nombre = plato.getNombre().trim();
        validarNombre(nombre, null);
        List<IngredienteEntity> ingredientes = resolverIngredientes(ingredienteIds);

        PlatoEntity entity = mapper.toEntity(plato);
        entity.setId(null);
        entity.setNombre(nombre);
        entity.setActivo(true);
        entity.setIngredientes(ingredientes);
        Plato creado = mapper.toDomain(platoRepository.save(entity));
        log.info("Plato creado: id={}", creado.getId());
        return creado;
    }

    @Override
    @Transactional(readOnly = true)
    public Plato obtenerPorId(Long id) {
        return mapper.toDomain(buscarEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Plato> listarTodos() {
        return platoRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Plato> listarCarta() {
        return platoRepository.findByActivoTrue().stream()
                .map(mapper::toDomain)
                .sorted(Comparator.comparing(Plato::getId))
                .toList();
    }

    @Override
    @Transactional
    public Plato actualizar(Long id, Plato cambios, List<Long> ingredienteIds) {
        PlatoEntity plato = buscarEntity(id);
        String nombre = cambios.getNombre().trim();
        validarNombre(nombre, id);
        // Resolver antes de mutar: un ID inexistente no debe dejar cambios parciales.
        List<IngredienteEntity> ingredientes = resolverIngredientes(ingredienteIds);
        plato.setNombre(nombre);
        plato.setDescripcion(cambios.getDescripcion());
        plato.setPrecio(cambios.getPrecio());
        plato.setCombo(cambios.isCombo());
        plato.setIngredientes(ingredientes);
        Plato actualizado = mapper.toDomain(platoRepository.save(plato));
        log.info("Plato actualizado: id={}", id);
        return actualizado;
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        PlatoEntity plato = buscarEntity(id);
        plato.setActivo(false);
        platoRepository.save(plato);
        log.info("Plato desactivado: id={}", id);
    }

    private void validarNombre(String nombre, Long idExcluido) {
        boolean duplicado = idExcluido == null
                ? platoRepository.existsByNombreIgnoreCase(nombre)
                : platoRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idExcluido);
        if (duplicado) {
            log.warn("Nombre de plato duplicado: {}", nombre);
            throw new ResourceAlreadyExistsException("Ya existe un plato con nombre: " + nombre);
        }
    }

    private PlatoEntity buscarEntity(Long id) {
        return platoRepository.findById(id).orElseThrow(() -> {
            log.warn("Plato inexistente: id={}", id);
            return new ResourceNotFoundException("No existe el plato con id: " + id);
        });
    }

    private List<IngredienteEntity> resolverIngredientes(List<Long> ids) {
        return new ArrayList<>(ids.stream().map(id -> ingredienteRepository.findById(id).orElseThrow(() -> {
            log.warn("Ingrediente inexistente: id={}", id);
            return new ResourceNotFoundException("No existe el ingrediente con id: " + id);
        })).toList());
    }
}
