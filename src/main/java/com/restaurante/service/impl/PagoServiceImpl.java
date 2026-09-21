package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.CuentaService;
import com.restaurante.service.PagoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {
    private final CuentaService cuentaService;
    private final ConcurrentHashMap<Long, Pago> pagos = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @Override
    public synchronized Pago registrarPago(Long cuentaId) {
        Cuenta cuenta = obtenerCuenta(cuentaId);
        if (cuenta.getEstado() != EstadoCuenta.ABIERTA) {
            log.warn("Intento de pago para cuenta ya cerrada: cuentaId={}", cuentaId);
            throw new BusinessRuleException("La cuenta con id " + cuentaId + " ya está cerrada");
        }
        if (buscarPorCuenta(cuentaId) != null) {
            log.warn("Intento de pago duplicado: cuentaId={}", cuentaId);
            throw new BusinessRuleException("La cuenta con id " + cuentaId + " ya tiene un pago registrado");
        }

        BigDecimal monto = cuenta.calcularTotal();
        LocalDateTime fechaHora = LocalDateTime.now();
        Pago pago = Pago.builder()
                .id(secuencia.incrementAndGet())
                .cuentaId(cuentaId)
                .monto(monto)
                .fechaHora(fechaHora)
                .build();

        pagos.put(pago.getId(), pago);
        cuenta.setEstado(EstadoCuenta.CERRADA);
        cuenta.setFechaCierre(fechaHora);
        log.info("Pago registrado: id={}, cuentaId={}", pago.getId(), cuentaId);
        log.info("Cuenta cerrada como consecuencia del pago: cuentaId={}, pagoId={}",
                cuentaId, pago.getId());
        return pago;
    }

    @Override
    public Pago obtenerPorId(Long pagoId) {
        Pago pago = pagos.get(pagoId);
        if (pago == null) {
            log.warn("Pago inexistente: id={}", pagoId);
            throw new ResourceNotFoundException("No existe el pago con id: " + pagoId);
        }
        return pago;
    }

    @Override
    public Pago obtenerPorCuenta(Long cuentaId) {
        obtenerCuenta(cuentaId);
        Pago pago = buscarPorCuenta(cuentaId);
        if (pago == null) {
            log.warn("Pago inexistente para cuenta: cuentaId={}", cuentaId);
            throw new ResourceNotFoundException(
                    "La cuenta con id " + cuentaId + " todavía no tiene un pago registrado");
        }
        return pago;
    }

    @Override
    public List<Pago> listar() {
        return pagos.values().stream()
                .sorted(Comparator.comparing(Pago::getId))
                .toList();
    }

    private Cuenta obtenerCuenta(Long cuentaId) {
        try {
            return cuentaService.obtenerPorId(cuentaId);
        } catch (ResourceNotFoundException exception) {
            log.warn("Cuenta inexistente al consultar pago: cuentaId={}", cuentaId);
            throw exception;
        }
    }

    private Pago buscarPorCuenta(Long cuentaId) {
        return pagos.values().stream()
                .filter(pago -> Objects.equals(pago.getCuentaId(), cuentaId))
                .findFirst()
                .orElse(null);
    }
}
