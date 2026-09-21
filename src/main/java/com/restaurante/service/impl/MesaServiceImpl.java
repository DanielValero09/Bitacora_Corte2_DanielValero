package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Mesa;
import com.restaurante.service.MesaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class MesaServiceImpl implements MesaService {
    private final Map<Long, Mesa> mesas = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public synchronized Mesa crear(Mesa mesa) {
        if (mesas.values().stream()
                .anyMatch(actual -> actual.getNumero().equals(mesa.getNumero()))) {
            log.warn("Intento de crear mesa con numero duplicado: {}", mesa.getNumero());
            throw new ResourceAlreadyExistsException(
                    "Ya existe una mesa con numero: " + mesa.getNumero());
        }

        mesa.setId(secuencia.incrementAndGet());
        mesas.put(mesa.getId(), mesa);
        log.info("Mesa creada: id={}, numero={}", mesa.getId(), mesa.getNumero());
        return mesa;
    }

    @Override
    public Mesa obtenerPorId(Long id) {
        Mesa mesa = mesas.get(id);
        if (mesa == null) {
            log.warn("Mesa inexistente: id={}", id);
            throw new ResourceNotFoundException("No existe la mesa con id: " + id);
        }
        return mesa;
    }

    @Override
    public List<Mesa> listar() {
        return mesas.values().stream()
                .sorted(Comparator.comparing(Mesa::getId))
                .toList();
    }
}
