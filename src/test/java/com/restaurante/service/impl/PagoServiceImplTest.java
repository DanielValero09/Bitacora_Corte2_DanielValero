package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.CuentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {
    @Mock
    private CuentaService cuentaService;
    @InjectMocks
    private PagoServiceImpl service;

    @Test
    void registraPagoConDatosCalculadosYCierraLaCuenta() {
        Cuenta cuenta = cuentaAbierta(10L, new BigDecimal("42.50"));
        when(cuentaService.obtenerPorId(10L)).thenReturn(cuenta);

        Pago pago = service.registrarPago(10L);

        assertEquals(1L, pago.getId());
        assertEquals(10L, pago.getCuentaId());
        assertEquals(new BigDecimal("42.50"), pago.getMonto());
        assertNotNull(pago.getFechaHora());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(pago.getFechaHora(), cuenta.getFechaCierre());
        assertSame(pago, service.obtenerPorId(pago.getId()));
        verify(cuentaService).obtenerPorId(10L);
    }

    @Test
    void generaIdsConsecutivosParaPagosDeCuentasDiferentes() {
        Cuenta primera = cuentaAbierta(1L, BigDecimal.ONE);
        Cuenta segunda = cuentaAbierta(2L, BigDecimal.TWO);
        when(cuentaService.obtenerPorId(1L)).thenReturn(primera);
        when(cuentaService.obtenerPorId(2L)).thenReturn(segunda);

        Pago pago1 = service.registrarPago(1L);
        Pago pago2 = service.registrarPago(2L);

        assertEquals(1L, pago1.getId());
        assertEquals(2L, pago2.getId());
        assertNotEquals(pago1.getId(), pago2.getId());
    }

    @Test
    void cuentaInexistenteImpideRegistrarPago() {
        when(cuentaService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Cuenta inexistente"));

        assertThrows(ResourceNotFoundException.class, () -> service.registrarPago(99L));
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void cuentaCerradaImpideRegistrarPago() {
        Cuenta cuenta = cuentaAbierta(1L, BigDecimal.TEN);
        cuenta.setEstado(EstadoCuenta.CERRADA);
        when(cuentaService.obtenerPorId(1L)).thenReturn(cuenta);

        assertThrows(BusinessRuleException.class, () -> service.registrarPago(1L));
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void pagoDuplicadoEsRechazado() {
        Cuenta cuenta = cuentaAbierta(1L, BigDecimal.TEN);
        when(cuentaService.obtenerPorId(1L)).thenReturn(cuenta);
        service.registrarPago(1L);
        cuenta.setEstado(EstadoCuenta.ABIERTA);

        BusinessRuleException error = assertThrows(
                BusinessRuleException.class, () -> service.registrarPago(1L));

        assertTrue(error.getMessage().contains("pago registrado"));
        assertEquals(1, service.listar().size());
    }

    @Test
    void obtienePagoPorId() {
        Pago pago = registrarPago(1L);

        assertSame(pago, service.obtenerPorId(pago.getId()));
    }

    @Test
    void pagoPorIdInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void obtienePagoPorCuenta() {
        Pago pago = registrarPago(1L);

        assertSame(pago, service.obtenerPorCuenta(1L));
    }

    @Test
    void cuentaSinPagoLanzaExcepcionAlConsultarPago() {
        Cuenta cuenta = cuentaAbierta(1L, BigDecimal.ZERO);
        when(cuentaService.obtenerPorId(1L)).thenReturn(cuenta);

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorCuenta(1L));
    }

    @Test
    void cuentaInexistenteLanzaExcepcionAlConsultarPago() {
        when(cuentaService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Cuenta inexistente"));

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorCuenta(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarDevuelvePagosOrdenadosPorId() {
        Pago primero = registrarPago(2L);
        Pago segundo = registrarPago(1L);

        assertEquals(List.of(primero, segundo), service.listar());
    }

    @Test
    void pagoUsaPrecioCongeladoAunqueCambieElPrecioActualDelPlato() {
        Plato plato = Plato.builder().id(8L).precio(new BigDecimal("20.00")).build();
        ItemPedido item = ItemPedido.builder()
                .id(1L)
                .platoId(plato.getId())
                .nombrePlato("Hamburguesa")
                .precioCongelado(plato.getPrecio())
                .cantidad(1)
                .build();
        Pedido pedido = Pedido.builder().id(1L).cuentaId(1L).items(new ArrayList<>(List.of(item))).build();
        Cuenta cuenta = Cuenta.builder()
                .id(1L)
                .estado(EstadoCuenta.ABIERTA)
                .pedidos(new ArrayList<>(List.of(pedido)))
                .build();
        when(cuentaService.obtenerPorId(1L)).thenReturn(cuenta);

        plato.setPrecio(new BigDecimal("30.00"));
        Pago pago = service.registrarPago(1L);

        assertEquals(new BigDecimal("20.00"), cuenta.calcularTotal());
        assertEquals(new BigDecimal("20.00"), pago.getMonto());
    }

    @Test
    void dosIntentosConcurrentesSoloRegistranUnPago() throws Exception {
        Cuenta cuenta = cuentaAbierta(1L, BigDecimal.TEN);
        when(cuentaService.obtenerPorId(1L)).thenReturn(cuenta);
        CountDownLatch inicio = new CountDownLatch(1);

        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<Object> primero = ejecutor.submit(() -> pagarTrasSenal(inicio));
            Future<Object> segundo = ejecutor.submit(() -> pagarTrasSenal(inicio));
            inicio.countDown();

            Object resultado1 = primero.get();
            Object resultado2 = segundo.get();

            assertTrue(resultado1 instanceof Pago || resultado2 instanceof Pago);
            assertTrue(resultado1 instanceof BusinessRuleException
                    || resultado2 instanceof BusinessRuleException);
            assertEquals(1, service.listar().size());
            assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
            assertNotNull(cuenta.getFechaCierre());
        }
    }

    private Pago registrarPago(Long cuentaId) {
        Cuenta cuenta = cuentaAbierta(cuentaId, BigDecimal.valueOf(cuentaId));
        when(cuentaService.obtenerPorId(cuentaId)).thenReturn(cuenta);
        return service.registrarPago(cuentaId);
    }

    private Object pagarTrasSenal(CountDownLatch inicio) throws InterruptedException {
        inicio.await();
        try {
            return service.registrarPago(1L);
        } catch (BusinessRuleException exception) {
            return assertInstanceOf(BusinessRuleException.class, exception);
        }
    }

    private Cuenta cuentaAbierta(Long id, BigDecimal total) {
        ItemPedido item = ItemPedido.builder()
                .id(1L)
                .precioCongelado(total)
                .cantidad(1)
                .build();
        Pedido pedido = Pedido.builder()
                .id(1L)
                .cuentaId(id)
                .items(new ArrayList<>(List.of(item)))
                .build();
        return Cuenta.builder()
                .id(id)
                .mesaId(id)
                .estado(EstadoCuenta.ABIERTA)
                .fechaApertura(LocalDateTime.now())
                .pedidos(new ArrayList<>(List.of(pedido)))
                .build();
    }
}
