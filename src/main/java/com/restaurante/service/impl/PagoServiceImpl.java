package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.CuentaEntityMapper;
import com.restaurante.mapper.PagoEntityMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.PagoEntity;
import com.restaurante.model.entity.PedidoEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.PagoRepository;
import com.restaurante.service.PagoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoServiceImpl implements PagoService {
    private final CuentaRepository cuentaRepository;
    private final PagoRepository pagoRepository;
    private final CuentaEntityMapper cuentaMapper;
    private final PagoEntityMapper pagoMapper;

    @Override
    @Transactional
    public Pago registrarPago(Long cuentaId) {
        CuentaEntity cuentaEntity = buscarCuentaParaActualizar(cuentaId);
        if (cuentaEntity.getEstado() != EstadoCuenta.ABIERTA) {
            throw new BusinessRuleException("La cuenta con id " + cuentaId + " ya está cerrada");
        }
        if (pagoRepository.existsByCuentaId(cuentaId)) {
            throw pagoDuplicado(cuentaId);
        }

        // Cuenta -> Pedido (id ascendente), tambien con un contexto precargado.
        // Cuenta no propaga REFRESH: sus pedidos deben releerse explicitamente.
        cuentaEntity.getPedidos().stream()
                .sorted(Comparator.comparing(PedidoEntity::getId))
                .forEach(cuentaRepository::refreshForUpdate);
        Cuenta cuenta = cuentaMapper.toDomain(cuentaEntity);
        BigDecimal monto = cuenta.calcularTotal();
        LocalDateTime fechaHora = LocalDateTime.now();
        cuentaEntity.setEstado(EstadoCuenta.CERRADA);
        cuentaEntity.setFechaCierre(fechaHora);
        cuentaRepository.save(cuentaEntity);

        PagoEntity pago = PagoEntity.builder()
                .cuenta(cuentaEntity)
                .monto(monto)
                .fechaHora(fechaHora)
                .build();
        try {
            Pago creado = pagoMapper.toDomain(pagoRepository.saveAndFlush(pago));
            log.info("Pago registrado: id={}, cuentaId={}", creado.getId(), cuentaId);
            return creado;
        } catch (DataIntegrityViolationException exception) {
            throw pagoDuplicado(cuentaId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Pago obtenerPorId(Long pagoId) {
        return pagoMapper.toDomain(pagoRepository.findById(pagoId).orElseThrow(() -> {
            log.warn("Pago inexistente: id={}", pagoId);
            return new ResourceNotFoundException("No existe el pago con id: " + pagoId);
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public Pago obtenerPorCuenta(Long cuentaId) {
        validarCuentaExiste(cuentaId);
        return pagoRepository.findByCuentaId(cuentaId)
                .map(pagoMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La cuenta con id " + cuentaId + " todavía no tiene un pago registrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pago> listar() {
        return pagoRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(pagoMapper::toDomain)
                .toList();
    }

    private CuentaEntity buscarCuentaParaActualizar(Long cuentaId) {
        CuentaEntity cuenta = cuentaRepository.findByIdForUpdate(cuentaId).orElseThrow(() -> {
            log.warn("Cuenta inexistente al consultar pago: cuentaId={}", cuentaId);
            return new ResourceNotFoundException("No existe la cuenta con id: " + cuentaId);
        });
        cuentaRepository.refreshForUpdate(cuenta);
        return cuenta;
    }

    private void validarCuentaExiste(Long cuentaId) {
        if (!cuentaRepository.existsById(cuentaId)) {
            throw new ResourceNotFoundException("No existe la cuenta con id: " + cuentaId);
        }
    }

    private BusinessRuleException pagoDuplicado(Long cuentaId) {
        log.warn("Intento de pago duplicado: cuentaId={}", cuentaId);
        return new BusinessRuleException(
                "La cuenta con id " + cuentaId + " ya tiene un pago registrado");
    }
}
