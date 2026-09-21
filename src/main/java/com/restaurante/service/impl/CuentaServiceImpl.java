package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.CuentaService;
import com.restaurante.service.MesaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService {
    private final MesaService mesaService;
    private final Map<Long, Cuenta> cuentas = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public synchronized Cuenta abrirCuenta(Long mesaId) {
        mesaService.obtenerPorId(mesaId);

        boolean tieneCuentaAbierta = cuentas.values().stream()
                .anyMatch(cuenta -> Objects.equals(cuenta.getMesaId(), mesaId)
                        && cuenta.getEstado() == EstadoCuenta.ABIERTA);
        if (tieneCuentaAbierta) {
            log.warn("Intento de abrir segunda cuenta para la mesa: id={}", mesaId);
            throw new BusinessRuleException(
                    "La mesa con id " + mesaId + " ya tiene una cuenta abierta");
        }

        Cuenta cuenta = Cuenta.builder()
                .id(secuencia.incrementAndGet())
                .mesaId(mesaId)
                .estado(EstadoCuenta.ABIERTA)
                .fechaApertura(LocalDateTime.now())
                .fechaCierre(null)
                .pedidos(new ArrayList<>())
                .build();
        cuentas.put(cuenta.getId(), cuenta);
        log.info("Cuenta abierta: id={}, mesaId={}", cuenta.getId(), mesaId);
        return cuenta;
    }

    @Override
    public Cuenta obtenerPorId(Long cuentaId) {
        Cuenta cuenta = cuentas.get(cuentaId);
        if (cuenta == null) {
            log.warn("Cuenta inexistente: id={}", cuentaId);
            throw new ResourceNotFoundException("No existe la cuenta con id: " + cuentaId);
        }
        return cuenta;
    }

    @Override
    public Cuenta obtenerCuentaAbiertaPorMesa(Long mesaId) {
        mesaService.obtenerPorId(mesaId);
        return cuentas.values().stream()
                .filter(cuenta -> Objects.equals(cuenta.getMesaId(), mesaId))
                .filter(cuenta -> cuenta.getEstado() == EstadoCuenta.ABIERTA)
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Mesa sin cuenta abierta: id={}", mesaId);
                    return new ResourceNotFoundException(
                            "La mesa con id " + mesaId + " no tiene una cuenta abierta");
                });
    }

    @Override
    public List<Cuenta> listar() {
        return cuentas.values().stream()
                .sorted(Comparator.comparing(Cuenta::getId))
                .toList();
    }
}
