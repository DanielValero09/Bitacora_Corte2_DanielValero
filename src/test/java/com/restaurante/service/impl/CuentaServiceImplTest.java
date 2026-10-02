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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {
    @Mock
    private MesaRepository mesaRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private CuentaEntityMapper mapper;
    private CuentaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuentaServiceImpl(mesaRepository, cuentaRepository, mapper);
    }

    @Test
    void abrirCuentaBloqueaMesaEInicializaDatos() {
        MesaEntity mesa = MesaEntity.builder().id(1L).numero(10).build();
        when(mesaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mesa));
        when(cuentaRepository.save(any())).thenAnswer(invocation -> {
            CuentaEntity entity = invocation.getArgument(0);
            entity.setId(5L);
            return entity;
        });
        when(mapper.toDomain(any())).thenAnswer(invocation -> {
            CuentaEntity entity = invocation.getArgument(0);
            return Cuenta.builder().id(entity.getId()).mesaId(entity.getMesa().getId())
                    .estado(entity.getEstado()).fechaApertura(entity.getFechaApertura())
                    .fechaCierre(entity.getFechaCierre()).build();
        });

        Cuenta cuenta = service.abrirCuenta(1L);

        assertEquals(5L, cuenta.getId());
        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        assertEquals(1L, cuenta.getMesaId());
        assertTrue(cuenta.getPedidos().isEmpty());
        assertNotNull(cuenta.getFechaApertura());
        assertNull(cuenta.getFechaCierre());
        ArgumentCaptor<CuentaEntity> captor = ArgumentCaptor.forClass(CuentaEntity.class);
        verify(cuentaRepository).save(captor.capture());
        assertEquals(mesa, captor.getValue().getMesa());
        assertNull(captor.getValue().getCuentaAbierta());
        assertTrue(captor.getValue().getPedidos().isEmpty());
    }

    @Test
    void mesaInexistenteImpideAbrir() {
        when(mesaRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.abrirCuenta(99L));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void segundaCuentaAbiertaEsConflicto() {
        when(mesaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(
                MesaEntity.builder().id(1L).build()));
        when(cuentaRepository.existsByMesaIdAndEstado(1L, EstadoCuenta.ABIERTA))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.abrirCuenta(1L));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void permiteReaperturaCuandoNoHayCuentaAbierta() {
        when(mesaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(
                MesaEntity.builder().id(1L).build()));
        when(cuentaRepository.existsByMesaIdAndEstado(1L, EstadoCuenta.ABIERTA))
                .thenReturn(false);
        when(cuentaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Cuenta esperada = Cuenta.builder().mesaId(1L).estado(EstadoCuenta.ABIERTA).build();
        when(mapper.toDomain(any())).thenReturn(esperada);

        assertEquals(esperada, service.abrirCuenta(1L));
    }

    @Test
    void obtenerCuentaExistente() {
        CuentaEntity entity = CuentaEntity.builder().id(1L).build();
        Cuenta domain = Cuenta.builder().id(1L).build();
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);
        assertEquals(domain, service.obtenerPorId(1L));
    }

    @Test
    void cuentaInexistenteLanzaExcepcion() {
        when(cuentaRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void obtieneCuentaAbiertaConQueryDerivada() {
        CuentaEntity entity = CuentaEntity.builder().id(2L).build();
        Cuenta domain = Cuenta.builder().id(2L).build();
        when(mesaRepository.existsById(1L)).thenReturn(true);
        when(cuentaRepository.findByMesaIdAndEstado(1L, EstadoCuenta.ABIERTA))
                .thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);
        assertEquals(domain, service.obtenerCuentaAbiertaPorMesa(1L));
    }

    @Test
    void mesaSinCuentaAbiertaLanzaExcepcion() {
        when(mesaRepository.existsById(1L)).thenReturn(true);
        when(cuentaRepository.findByMesaIdAndEstado(1L, EstadoCuenta.ABIERTA))
                .thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerCuentaAbiertaPorMesa(1L));
    }

    @Test
    void mesaInexistenteAlConsultarCuentaAbiertaPropagaExcepcion() {
        when(mesaRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerCuentaAbiertaPorMesa(99L));
        verify(cuentaRepository, never()).findByMesaIdAndEstado(any(), any());
    }

    @Test
    void listarVacio() {
        when(cuentaRepository.findAll(any(Sort.class))).thenReturn(List.of());
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarMapeaElOrdenSolicitadoAlRepository() {
        CuentaEntity primera = CuentaEntity.builder().id(1L).build();
        CuentaEntity segunda = CuentaEntity.builder().id(2L).build();
        Cuenta dominioPrimera = Cuenta.builder().id(1L).build();
        Cuenta dominioSegunda = Cuenta.builder().id(2L).build();
        when(cuentaRepository.findAll(any(Sort.class))).thenReturn(List.of(primera, segunda));
        when(mapper.toDomain(primera)).thenReturn(dominioPrimera);
        when(mapper.toDomain(segunda)).thenReturn(dominioSegunda);

        assertEquals(List.of(dominioPrimera, dominioSegunda), service.listar());
        verify(cuentaRepository).findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void reaperturaCreaOtraCuentaSinAlterarLaCerrada() {
        MesaEntity mesa = MesaEntity.builder().id(1L).numero(10).build();
        when(mesaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mesa));
        when(cuentaRepository.existsByMesaIdAndEstado(1L, EstadoCuenta.ABIERTA))
                .thenReturn(false);
        when(cuentaRepository.save(any())).thenAnswer(invocation -> {
            CuentaEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });
        Cuenta nueva = Cuenta.builder().id(2L).mesaId(1L).estado(EstadoCuenta.ABIERTA).build();
        when(mapper.toDomain(any())).thenReturn(nueva);

        Cuenta abierta = service.abrirCuenta(1L);

        assertEquals(2L, abierta.getId());
        assertEquals(EstadoCuenta.ABIERTA, abierta.getEstado());
    }
}
