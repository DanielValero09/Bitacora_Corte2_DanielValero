package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.CuentaEntityMapper;
import com.restaurante.mapper.PagoEntityMapper;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.PagoEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.PagoRepository;
import com.restaurante.service.PlatoService;
import com.restaurante.support.RelationalTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {
    @Mock
    private PlatoService platoService;
    private RelationalTestFixture persistence;
    private PagoServiceImpl service;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        persistence = new RelationalTestFixture(platoService);
        service = persistence.pagos();
        Mesa mesa = persistence.mesas().crear(Mesa.builder().numero(1).build());
        cuenta = persistence.cuentas().abrirCuenta(mesa.getId());
    }

    @Test
    void registrarPagoUsaTotalCongeladoYCierraCuentaConMismaFecha() {
        Plato plato = Plato.builder().id(7L).nombre("Hamburguesa")
                .precio(new BigDecimal("20.00")).activo(true).build();
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = persistence.pedidos().crear(cuenta.getId());
        persistence.pedidos().agregarItem(pedido.getId(), 7L, 2);
        plato.setPrecio(new BigDecimal("35.00"));

        Pago pago = service.registrarPago(cuenta.getId());
        Cuenta cerrada = persistence.cuentas().obtenerPorId(cuenta.getId());

        assertNotNull(pago.getId());
        assertEquals(new BigDecimal("40.00"), pago.getMonto());
        assertEquals(EstadoCuenta.CERRADA, cerrada.getEstado());
        assertEquals(pago.getFechaHora(), cerrada.getFechaCierre());
    }

    @Test
    void pagoDuplicadoSeRechaza() {
        service.registrarPago(cuenta.getId());
        assertThrows(BusinessRuleException.class, () -> service.registrarPago(cuenta.getId()));
        assertEquals(1, service.listar().size());
    }

    @Test
    void cuentaInexistenteSeRechaza() {
        assertThrows(ResourceNotFoundException.class, () -> service.registrarPago(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorCuenta(99L));
    }

    @Test
    void consultasNotFoundYListaVacia() {
        assertTrue(service.listar().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorCuenta(cuenta.getId()));
    }

    @Test
    void pagoPuedeConsultarsePorIdYCuenta() {
        Pago pago = service.registrarPago(cuenta.getId());
        assertEquals(pago.getId(), service.obtenerPorId(pago.getId()).getId());
        assertEquals(pago.getId(), service.obtenerPorCuenta(cuenta.getId()).getId());
        assertEquals(1, service.listar().size());
    }

    @Test
    void cerrarConPagoPermiteNuevaCuentaParaLaMesa() {
        Long mesaId = cuenta.getMesaId();
        service.registrarPago(cuenta.getId());

        Cuenta nueva = persistence.cuentas().abrirCuenta(mesaId);

        assertNotEquals(cuenta.getId(), nueva.getId());
        assertEquals(EstadoCuenta.ABIERTA, nueva.getEstado());
    }

    @Test
    void cuentaCerradaImpideRegistrarOtroPago() {
        service.registrarPago(cuenta.getId());

        assertThrows(BusinessRuleException.class, () -> service.registrarPago(cuenta.getId()));
        assertEquals(1, service.listar().size());
    }

    @Test
    void idsDePagosSonGeneradosParaCuentasDiferentes() {
        Mesa otraMesa = persistence.mesas().crear(Mesa.builder().numero(2).build());
        Cuenta otraCuenta = persistence.cuentas().abrirCuenta(otraMesa.getId());

        Pago primero = service.registrarPago(cuenta.getId());
        Pago segundo = service.registrarPago(otraCuenta.getId());

        assertNotEquals(primero.getId(), segundo.getId());
    }

    @Test
    void pagoExistenteSeDetectaAntesDeGuardar() {
        CuentaRepository cuentas = mock(CuentaRepository.class);
        PagoRepository pagos = mock(PagoRepository.class);
        CuentaEntityMapper cuentaMapper = mock(CuentaEntityMapper.class);
        PagoEntityMapper pagoMapper = mock(PagoEntityMapper.class);
        CuentaEntity cuentaAbierta = CuentaEntity.builder().id(1L)
                .estado(EstadoCuenta.ABIERTA).build();
        when(cuentas.findByIdForUpdate(1L)).thenReturn(Optional.of(cuentaAbierta));
        when(pagos.existsByCuentaId(1L)).thenReturn(true);

        PagoServiceImpl directo = new PagoServiceImpl(cuentas, pagos, cuentaMapper, pagoMapper);

        assertThrows(BusinessRuleException.class, () -> directo.registrarPago(1L));
        verify(pagos, never()).saveAndFlush(any());
    }

    @Test
    void restriccionUnicaConcurrenteSeTraduceAPagoDuplicado() {
        CuentaRepository cuentas = mock(CuentaRepository.class);
        PagoRepository pagos = mock(PagoRepository.class);
        CuentaEntityMapper cuentaMapper = mock(CuentaEntityMapper.class);
        PagoEntityMapper pagoMapper = mock(PagoEntityMapper.class);
        CuentaEntity cuentaAbierta = CuentaEntity.builder().id(1L)
                .estado(EstadoCuenta.ABIERTA).build();
        Cuenta dominio = Cuenta.builder().id(1L).pedidos(List.of()).build();
        when(cuentas.findByIdForUpdate(1L)).thenReturn(Optional.of(cuentaAbierta));
        when(cuentaMapper.toDomain(cuentaAbierta)).thenReturn(dominio);
        when(pagos.saveAndFlush(any(PagoEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_pagos_cuenta"));

        PagoServiceImpl directo = new PagoServiceImpl(cuentas, pagos, cuentaMapper, pagoMapper);

        assertThrows(BusinessRuleException.class, () -> directo.registrarPago(1L));
    }

    @Test
    void cuentaSinPedidosGeneraPagoDeMontoCero() {
        Pago pago = service.registrarPago(cuenta.getId());

        assertEquals(BigDecimal.ZERO, pago.getMonto());
    }

    @Test
    void cierreDeCuentaOcurreAntesDePersistirElPago() {
        CuentaRepository cuentas = mock(CuentaRepository.class);
        PagoRepository pagos = mock(PagoRepository.class);
        CuentaEntityMapper cuentaMapper = mock(CuentaEntityMapper.class);
        PagoEntityMapper pagoMapper = mock(PagoEntityMapper.class);
        CuentaEntity cuentaAbierta = CuentaEntity.builder().id(1L)
                .estado(EstadoCuenta.ABIERTA).build();
        Cuenta dominio = Cuenta.builder().id(1L).pedidos(List.of()).build();
        Pago pago = Pago.builder().id(1L).cuentaId(1L).monto(BigDecimal.ZERO)
                .fechaHora(LocalDateTime.now()).build();
        when(cuentas.findByIdForUpdate(1L)).thenReturn(Optional.of(cuentaAbierta));
        when(cuentaMapper.toDomain(cuentaAbierta)).thenReturn(dominio);
        when(pagos.saveAndFlush(any(PagoEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoMapper.toDomain(any(PagoEntity.class))).thenReturn(pago);
        PagoServiceImpl directo = new PagoServiceImpl(cuentas, pagos, cuentaMapper, pagoMapper);

        directo.registrarPago(1L);

        InOrder orden = inOrder(cuentas, pagos);
        orden.verify(cuentas).save(cuentaAbierta);
        orden.verify(pagos).saveAndFlush(any(PagoEntity.class));
        assertEquals(EstadoCuenta.CERRADA, cuentaAbierta.getEstado());
        assertNotNull(cuentaAbierta.getFechaCierre());
    }

    @Test
    void listarPagosConservaElOrdenDelRepository() {
        Mesa otraMesa = persistence.mesas().crear(Mesa.builder().numero(2).build());
        Cuenta otraCuenta = persistence.cuentas().abrirCuenta(otraMesa.getId());
        Pago primero = service.registrarPago(cuenta.getId());
        Pago segundo = service.registrarPago(otraCuenta.getId());

        assertEquals(List.of(primero.getId(), segundo.getId()),
                service.listar().stream().map(Pago::getId).toList());
    }

    @Test
    void pagoConservaPrecioCongeladoAunqueCambieElPlato() {
        Plato plato = Plato.builder().id(7L).nombre("Hamburguesa")
                .precio(new BigDecimal("20000.00")).activo(true).build();
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = persistence.pedidos().crear(cuenta.getId());
        persistence.pedidos().agregarItem(pedido.getId(), 7L, 1);
        plato.setPrecio(new BigDecimal("30000.00"));

        Pago pago = service.registrarPago(cuenta.getId());

        assertEquals(new BigDecimal("20000.00"), pago.getMonto());
    }
}
