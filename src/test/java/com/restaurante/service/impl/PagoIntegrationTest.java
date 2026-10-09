package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.PagoEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.service.PlatoService;
import com.restaurante.support.RelationalTestFixture;
import jakarta.persistence.Column;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagoIntegrationTest {

    @Test
    void pagoPermiteTerminarCocinaPeroImpideModificarContenido() {
        PlatoService platos = mock(PlatoService.class);
        Plato plato = plato();
        when(platos.obtenerPorId(1L)).thenReturn(plato);
        RelationalTestFixture fixture = new RelationalTestFixture(platos);
        Cuenta cuenta = abrirCuenta(fixture, 1);
        Pedido pedido = fixture.pedidos().crear(cuenta.getId());
        fixture.pedidos().agregarItem(pedido.getId(), 1L, 1);
        fixture.pedidos().confirmar(pedido.getId());
        fixture.pedidos().cambiarEstado(
                pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");

        fixture.pagos().registrarPago(cuenta.getId());

        assertEquals(EstadoCuenta.CERRADA,
                fixture.cuentas().obtenerPorId(cuenta.getId()).getEstado());
        assertThrows(BusinessRuleException.class,
                () -> fixture.pedidos().agregarItem(pedido.getId(), 1L, 1));
        fixture.pedidos().cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero");
        fixture.pedidos().cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero");
        assertEquals(EstadoPedido.ENTREGADO,
                fixture.pedidos().obtenerPorId(pedido.getId()).getEstado());
        assertEquals(3, fixture.pedidos().obtenerHistorial(pedido.getId()).size());
    }

    @Test
    void pagoCierraYPermiteReapertura() {
        RelationalTestFixture fixture = new RelationalTestFixture(mock(PlatoService.class));
        Cuenta cuenta = abrirCuenta(fixture, 12);

        Pago pago = fixture.pagos().registrarPago(cuenta.getId());
        Cuenta cerrada = fixture.cuentas().obtenerPorId(cuenta.getId());
        Cuenta nueva = fixture.cuentas().abrirCuenta(cuenta.getMesaId());

        assertEquals(EstadoCuenta.CERRADA, cerrada.getEstado());
        assertEquals(pago.getFechaHora(), cerrada.getFechaCierre());
        assertNotNull(cerrada.getFechaCierre());
        assertNotEquals(cuenta.getId(), nueva.getId());
    }

    @Test
    void seccionesCriticasDeclaranBloqueoPesimista() throws Exception {
        assertLock(MesaRepository.class, "findByIdForUpdate");
        assertLock(CuentaRepository.class, "findByIdForUpdate");
        assertLock(PedidoRepository.class, "findByIdForUpdate");
    }

    @Test
    void restriccionesDeBaseProtegenPagoYCuentaAbierta() throws Exception {
        Table pagoTable = PagoEntity.class.getAnnotation(Table.class);
        assertEquals("uk_pagos_cuenta", pagoTable.uniqueConstraints()[0].name());

        Table cuentaTable = CuentaEntity.class.getAnnotation(Table.class);
        assertEquals("uk_cuentas_mesa_abierta", cuentaTable.uniqueConstraints()[0].name());
        Column columna = CuentaEntity.class.getDeclaredField("cuentaAbierta")
                .getAnnotation(Column.class);
        assertTrue(columna.columnDefinition().contains("generated always"));
    }

    private void assertLock(Class<?> repository, String method) throws Exception {
        Lock lock = repository.getMethod(method, Long.class).getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    private Cuenta abrirCuenta(RelationalTestFixture fixture, int numero) {
        Mesa mesa = fixture.mesas().crear(Mesa.builder().numero(numero).build());
        return fixture.cuentas().abrirCuenta(mesa.getId());
    }

    private Plato plato() {
        return Plato.builder().id(1L).nombre("Hamburguesa")
                .precio(new BigDecimal("20.00")).activo(true).build();
    }
}
