package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.CuentaEntityMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.MesaEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.service.CuentaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaServiceImpl implements CuentaService {
    private final MesaRepository mesaRepository;
    private final CuentaRepository cuentaRepository;
    private final CuentaEntityMapper mapper;

    @Override
    @Transactional
    public Cuenta abrirCuenta(Long mesaId) {
        MesaEntity mesa = mesaRepository.findByIdForUpdate(mesaId).orElseThrow(() -> {
            log.warn("Mesa inexistente: id={}", mesaId);
            return new ResourceNotFoundException("No existe la mesa con id: " + mesaId);
        });
        if (cuentaRepository.existsByMesaIdAndEstado(mesaId, EstadoCuenta.ABIERTA)) {
            log.warn("Intento de abrir segunda cuenta para la mesa: id={}", mesaId);
            throw new BusinessRuleException(
                    "La mesa con id " + mesaId + " ya tiene una cuenta abierta");
        }

        CuentaEntity entity = CuentaEntity.builder()
                .mesa(mesa)
                .estado(EstadoCuenta.ABIERTA)
                .fechaApertura(LocalDateTime.now())
                .fechaCierre(null)
                .pedidos(new ArrayList<>())
                .build();
        Cuenta creada = mapper.toDomain(cuentaRepository.save(entity));
        log.info("Cuenta abierta: id={}, mesaId={}", creada.getId(), mesaId);
        return creada;
    }

    @Override
    @Transactional(readOnly = true)
    public Cuenta obtenerPorId(Long cuentaId) {
        return mapper.toDomain(buscarEntity(cuentaId));
    }

    @Override
    @Transactional(readOnly = true)
    public Cuenta obtenerCuentaAbiertaPorMesa(Long mesaId) {
        if (!mesaRepository.existsById(mesaId)) {
            log.warn("Mesa inexistente: id={}", mesaId);
            throw new ResourceNotFoundException("No existe la mesa con id: " + mesaId);
        }
        return cuentaRepository.findByMesaIdAndEstado(mesaId, EstadoCuenta.ABIERTA)
                .map(mapper::toDomain)
                .orElseThrow(() -> {
                    log.warn("Mesa sin cuenta abierta: id={}", mesaId);
                    return new ResourceNotFoundException(
                            "La mesa con id " + mesaId + " no tiene una cuenta abierta");
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuenta> listar() {
        return cuentaRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private CuentaEntity buscarEntity(Long id) {
        return cuentaRepository.findById(id).orElseThrow(() -> {
            log.warn("Cuenta inexistente: id={}", id);
            return new ResourceNotFoundException("No existe la cuenta con id: " + id);
        });
    }
}
