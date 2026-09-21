package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.service.IngredienteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class IngredienteServiceImpl implements IngredienteService {
    private final Map<Long, Ingrediente> ingredientes = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public synchronized Ingrediente crear(Ingrediente ingrediente) {
        String nombre = ingrediente.getNombre().trim();
        if (ingredientes.values().stream().anyMatch(actual ->
                actual.getNombre().trim().equalsIgnoreCase(nombre))) {
            log.warn("Intento de crear ingrediente duplicado: {}", nombre);
            throw new ResourceAlreadyExistsException("Ya existe un ingrediente con nombre: " + nombre);
        }
        ingrediente.setId(secuencia.incrementAndGet());
        ingrediente.setNombre(nombre);
        ingredientes.put(ingrediente.getId(), ingrediente);
        log.info("Ingrediente creado: id={}", ingrediente.getId());
        return ingrediente;
    }

    @Override
    public Ingrediente obtenerPorId(Long id) {
        Ingrediente ingrediente = ingredientes.get(id);
        if (ingrediente == null) {
            log.warn("Ingrediente inexistente: id={}", id);
            throw new ResourceNotFoundException("No existe el ingrediente con id: " + id);
        }
        return ingrediente;
    }

    @Override
    public List<Ingrediente> listar() {
        return ingredientes.values().stream()
                .sorted(Comparator.comparing(Ingrediente::getId)).toList();
    }

    @Override
    public synchronized Ingrediente cambiarDisponibilidad(Long id, boolean disponible) {
        Ingrediente ingrediente = obtenerPorId(id);
        // Conservar la instancia compartida por todos los platos.
        ingrediente.setDisponible(disponible);
        log.info("Disponibilidad de ingrediente modificada: id={}, disponible={}", id, disponible);
        return ingrediente;
    }

    @Override
    public List<Ingrediente> obtenerPorIds(List<Long> ids) {
        return ids.stream().map(this::obtenerPorId).toList();
    }
}
