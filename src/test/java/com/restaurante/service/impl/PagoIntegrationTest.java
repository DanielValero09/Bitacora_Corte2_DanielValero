package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.service.PlatoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagoIntegrationTest {

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
