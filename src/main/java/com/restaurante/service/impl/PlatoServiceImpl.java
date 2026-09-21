package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import com.restaurante.service.IngredienteService;
import com.restaurante.service.PlatoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatoServiceImpl implements PlatoService {
    private final IngredienteService ingredienteService;
    private final Map<Long, Plato> platos = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public synchronized Plato crear(Plato plato, List<Long> ingredienteIds) {
        String nombre = plato.getNombre().trim();
        validarNombre(nombre, null);
        List<Ingrediente> ingredientes = ingredienteService.obtenerPorIds(ingredienteIds);
        plato.setId(secuencia.incrementAndGet());
        plato.setNombre(nombre);
        plato.setActivo(true);
        plato.setIngredientes(ingredientes);
        platos.put(plato.getId(), plato);
        log.info("Plato creado: id={}", plato.getId());
        return plato;
    }

    @Override
    public Plato obtenerPorId(Long id) {
        Plato plato = platos.get(id);
        if (plato == null) {
            log.warn("Plato inexistente: id={}", id);
            throw new ResourceNotFoundException("No existe el plato con id: " + id);
        }
        return plato;
    }

    @Override
    public List<Plato> listarTodos() {
        return platos.values().stream().sorted(Comparator.comparing(Plato::getId)).toList();
    }

    @Override
    public List<Plato> listarCarta() {
        return listarTodos().stream().filter(Plato::isActivo).toList();
    }

    @Override
    public synchronized Plato actualizar(Long id, Plato cambios, List<Long> ingredienteIds) {
        Plato plato = obtenerPorId(id);
        String nombre = cambios.getNombre().trim();
        validarNombre(nombre, id);
        // Resolver antes de mutar: un ID inexistente no debe dejar cambios parciales.
        List<Ingrediente> ingredientes = ingredienteService.obtenerPorIds(ingredienteIds);
        plato.setNombre(nombre);
        plato.setDescripcion(cambios.getDescripcion());
        plato.setPrecio(cambios.getPrecio());
        plato.setCombo(cambios.isCombo());
        plato.setIngredientes(ingredientes);
        log.info("Plato actualizado: id={}", id);
        return plato;
    }

    @Override
    public synchronized void desactivar(Long id) {
        obtenerPorId(id).setActivo(false);
        log.info("Plato desactivado: id={}", id);
    }

    private void validarNombre(String nombre, Long idExcluido) {
        boolean duplicado = platos.values().stream().anyMatch(plato ->
                !plato.getId().equals(idExcluido)
                        && plato.getNombre().trim().equalsIgnoreCase(nombre));
        if (duplicado) {
            log.warn("Nombre de plato duplicado: {}", nombre);
            throw new ResourceAlreadyExistsException("Ya existe un plato con nombre: " + nombre);
        }
    }
}
