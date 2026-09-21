package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.MesaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {
    @Mock
    private MesaService mesaService;
    @InjectMocks
    private CuentaServiceImpl service;

    @Test
    void abrirCuentaInicializaTodosLosDatosControladosPorElServidor() {
        mesaExiste(1L);

        Cuenta cuenta = service.abrirCuenta(1L);

        assertEquals(1L, cuenta.getId());
        assertEquals(1L, cuenta.getMesaId());
        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        assertNotNull(cuenta.getFechaApertura());
        assertNull(cuenta.getFechaCierre());
        assertNotNull(cuenta.getPedidos());
        assertTrue(cuenta.getPedidos().isEmpty());
    }

    @Test
    void mesaInexistenteAlAbrirPropagaExcepcionYNoGuardaCuenta() {
        when(mesaService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Mesa inexistente"));

        assertThrows(ResourceNotFoundException.class, () -> service.abrirCuenta(99L));
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void segundaCuentaAbiertaParaMismaMesaLanzaExcepcion() {
        mesaExiste(1L);
        service.abrirCuenta(1L);

        assertThrows(BusinessRuleException.class, () -> service.abrirCuenta(1L));
        assertEquals(1, service.listar().size());
    }

    @Test
    void obtenerCuentaExistente() {
        mesaExiste(1L);
        Cuenta creada = service.abrirCuenta(1L);

        assertSame(creada, service.obtenerPorId(creada.getId()));
    }

    @Test
    void cuentaInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarContieneCuentasOrdenadasPorId() {
        mesaExiste(1L);
        mesaExiste(2L);
        Cuenta primera = service.abrirCuenta(1L);
        Cuenta segunda = service.abrirCuenta(2L);

        assertEquals(List.of(primera, segunda), service.listar());
    }

    @Test
    void obtieneCuentaAbiertaPorMesa() {
        mesaExiste(1L);
        Cuenta abierta = service.abrirCuenta(1L);

        assertSame(abierta, service.obtenerCuentaAbiertaPorMesa(1L));
    }

    @Test
    void mesaSinCuentaAbiertaLanzaExcepcion() {
        mesaExiste(1L);

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerCuentaAbiertaPorMesa(1L));
        assertTrue(error.getMessage().contains("no tiene una cuenta abierta"));
    }

    @Test
    void mesaInexistenteAlConsultarCuentaAbiertaPropagaExcepcion() {
        when(mesaService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Mesa inexistente"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerCuentaAbiertaPorMesa(99L));
    }

    @Test
    void permiteNuevaCuentaCuandoLaAnteriorYaNoEstaAbierta() {
        mesaExiste(1L);
        Cuenta anterior = service.abrirCuenta(1L);
        anterior.setEstado(EstadoCuenta.CERRADA);

        Cuenta nueva = service.abrirCuenta(1L);

        assertEquals(2L, nueva.getId());
        assertEquals(2, service.listar().size());
        assertSame(nueva, service.obtenerCuentaAbiertaPorMesa(1L));
    }

    @Test
    void aperturaConcurrenteSoloCreaUnaCuentaAbierta() throws Exception {
        mesaExiste(1L);
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<Object> primera = ejecutor.submit(() -> abrirTrasSenal(inicio));
            Future<Object> segunda = ejecutor.submit(() -> abrirTrasSenal(inicio));
            inicio.countDown();

            Object resultado1 = primera.get();
            Object resultado2 = segunda.get();

            assertTrue(resultado1 instanceof Cuenta || resultado2 instanceof Cuenta);
            assertTrue(resultado1 instanceof BusinessRuleException
                    || resultado2 instanceof BusinessRuleException);
            assertFalse(resultado1.getClass().equals(resultado2.getClass()));
            assertEquals(1, service.listar().size());
        }
    }

    private Object abrirTrasSenal(CountDownLatch inicio) throws InterruptedException {
        inicio.await();
        try {
            return service.abrirCuenta(1L);
        } catch (BusinessRuleException exception) {
            return assertInstanceOf(BusinessRuleException.class, exception);
        }
    }

    private void mesaExiste(Long id) {
        when(mesaService.obtenerPorId(id))
                .thenReturn(Mesa.builder().id(id).numero(id.intValue()).build());
    }
}
