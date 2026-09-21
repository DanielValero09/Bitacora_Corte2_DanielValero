package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.service.PlatoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagoIntegrationTest {

    @Test
    void pagoPermiteTerminarFlujoDeCocinaPeroImpideModificarContenido() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        PlatoService platos = mock(PlatoService.class);
        PedidoServiceImpl pedidos = new PedidoServiceImpl(cuentas, platos);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);
        Cuenta cuenta = cuentas.abrirCuenta(mesas.crear(Mesa.builder().numero(1).build()).getId());
        Plato plato = Plato.builder().id(1L).nombre("Hamburguesa")
                .precio(new BigDecimal("20.00")).activo(true).build();
        when(platos.obtenerPorId(1L)).thenReturn(plato);

        Pedido pedidoCocina = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedidoCocina.getId(), plato.getId(), 1);
        pedidos.confirmar(pedidoCocina.getId());
        pedidos.cambiarEstado(
                pedidoCocina.getId(), EstadoPedido.EN_PREPARACION, "cocina-inicio");

        Pedido pedidoEditable = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedidoEditable.getId(), plato.getId(), 1);
        ItemPedido itemEditable = pedidoEditable.getItems().getFirst();

        pagos.registrarPago(cuenta.getId());

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertThrows(BusinessRuleException.class,
                () -> pedidos.agregarItem(pedidoEditable.getId(), plato.getId(), 1));
        assertThrows(BusinessRuleException.class,
                () -> pedidos.actualizarCantidadItem(
                        pedidoEditable.getId(), itemEditable.getId(), 2));
        assertThrows(BusinessRuleException.class,
                () -> pedidos.eliminarItem(pedidoEditable.getId(), itemEditable.getId()));
        assertEquals(1, itemEditable.getCantidad());
        assertEquals(1, pedidoEditable.getItems().size());

        pedidos.cambiarEstado(pedidoCocina.getId(), EstadoPedido.LISTO, "cocina-fin");
        pedidos.cambiarEstado(pedidoCocina.getId(), EstadoPedido.ENTREGADO, "servicio");

        assertEquals(EstadoPedido.ENTREGADO, pedidoCocina.getEstado());
        assertEquals(3, pedidoCocina.getHistorialEstados().size());
        assertEquals(EstadoPedido.EN_PREPARACION,
                pedidoCocina.getHistorialEstados().get(1).getEstadoAnterior());
        assertEquals(EstadoPedido.LISTO,
                pedidoCocina.getHistorialEstados().get(1).getEstadoNuevo());
        assertEquals(EstadoPedido.LISTO,
                pedidoCocina.getHistorialEstados().get(2).getEstadoAnterior());
        assertEquals(EstadoPedido.ENTREGADO,
                pedidoCocina.getHistorialEstados().get(2).getEstadoNuevo());
    }

    @Test
    void pagoConcurrenteConAgregarItemConservaElTotalDeLaCuentaCerrada() throws Exception {
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        PlatoService platos = mock(PlatoService.class);
        PedidoServiceImpl pedidos = new PedidoServiceImpl(cuentas, platos);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);
        Cuenta cuenta = cuentas.abrirCuenta(mesas.crear(Mesa.builder().numero(1).build()).getId());
        Pedido pedido = pedidos.crear(cuenta.getId());
        Plato plato = Plato.builder().id(1L).nombre("Hamburguesa")
                .precio(new BigDecimal("20.00")).activo(true).build();
        CountDownLatch consultandoPlato = new CountDownLatch(1);
        CountDownLatch continuarEdicion = new CountDownLatch(1);
        when(platos.obtenerPorId(1L)).thenAnswer(invocacion -> {
            consultandoPlato.countDown();
            assertTrue(continuarEdicion.await(10, TimeUnit.SECONDS));
            return plato;
        });
        FutureTask<Pedido> edicion = new FutureTask<>(() -> pedidos.agregarItem(pedido.getId(), 1L, 1));
        FutureTask<Pago> pago = new FutureTask<>(() -> pagos.registrarPago(cuenta.getId()));
        Thread hiloEdicion = new Thread(edicion, "auditoria-edicion");
        Thread hiloPago = new Thread(pago, "auditoria-pago");
        try {
            hiloEdicion.start();
            assertTrue(consultandoPlato.await(10, TimeUnit.SECONDS));
            hiloPago.start();
            // Esperar una condición observable, sin depender de sleeps ni del orden del scheduler.
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!pago.isDone() && hiloPago.getState() != Thread.State.BLOCKED
                    && System.nanoTime() < limite) {
                Thread.onSpinWait();
            }
            assertTrue(pago.isDone() || hiloPago.getState() == Thread.State.BLOCKED);
            continuarEdicion.countDown();
            edicion.get(10, TimeUnit.SECONDS);
            Pago resultado = pago.get(10, TimeUnit.SECONDS);

            assertEquals(new BigDecimal("20.00"), resultado.getMonto());
            assertEquals(cuenta.calcularTotal(), resultado.getMonto());
            assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        } finally {
            continuarEdicion.countDown();
            hiloEdicion.join(TimeUnit.SECONDS.toMillis(10));
            hiloPago.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    @Test
    void pagarCierraCuentaYPermiteAbrirOtraParaLaMismaMesa() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);
        Mesa mesa = mesas.crear(Mesa.builder().numero(12).build());
        Cuenta cuentaA = cuentas.abrirCuenta(mesa.getId());

        Pago pago = pagos.registrarPago(cuentaA.getId());
        Cuenta cuentaB = cuentas.abrirCuenta(mesa.getId());

        assertEquals(EstadoCuenta.CERRADA, cuentaA.getEstado());
        assertEquals(pago.getFechaHora(), cuentaA.getFechaCierre());
        assertNotNull(cuentaA.getFechaCierre());
        assertNotEquals(cuentaA.getId(), cuentaB.getId());
        assertEquals(EstadoCuenta.ABIERTA, cuentaB.getEstado());
        assertSame(cuentaB, cuentas.obtenerCuentaAbiertaPorMesa(mesa.getId()));
    }

    @Test
    void cuentaPedidoPagoConservaTotalYCerrarCuentaImpideModificarPedido() {
        MesaServiceImpl mesas = new MesaServiceImpl();
        CuentaServiceImpl cuentas = new CuentaServiceImpl(mesas);
        PlatoService platos = mock(PlatoService.class);
        PedidoServiceImpl pedidos = new PedidoServiceImpl(cuentas, platos);
        PagoServiceImpl pagos = new PagoServiceImpl(cuentas);
        Mesa mesa = mesas.crear(Mesa.builder().numero(15).build());
        Cuenta cuenta = cuentas.abrirCuenta(mesa.getId());
        Plato plato = Plato.builder()
                .id(7L)
                .nombre("Hamburguesa")
                .precio(new BigDecimal("20.00"))
                .activo(true)
                .build();
        when(platos.obtenerPorId(7L)).thenReturn(plato);

        Pedido pedido = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedido.getId(), plato.getId(), 1);
        plato.setPrecio(new BigDecimal("30.00"));
        BigDecimal totalAntesDelPago = cuenta.calcularTotal();
        Pago pago = pagos.registrarPago(cuenta.getId());

        assertEquals(new BigDecimal("20.00"), totalAntesDelPago);
        assertEquals(totalAntesDelPago, pago.getMonto());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertThrows(BusinessRuleException.class,
                () -> pedidos.actualizarCantidadItem(pedido.getId(), 1L, 2));
        assertEquals(new BigDecimal("20.00"), cuenta.calcularTotal());
    }
}
