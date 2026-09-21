package com.restaurante.model.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuentaTest {

    @Test
    void calcularTotalSumaTodosLosPedidosAsociados() {
        Pedido primerPedido = Pedido.builder()
                .items(List.of(
                        ItemPedido.builder().precioCongelado(new BigDecimal("10.50")).cantidad(2).build(),
                        ItemPedido.builder().precioCongelado(new BigDecimal("3.25")).cantidad(3).build()))
                .build();
        Pedido segundoPedido = Pedido.builder()
                .items(List.of(
                        ItemPedido.builder().precioCongelado(new BigDecimal("5.10")).cantidad(2).build()))
                .build();
        Cuenta cuenta = Cuenta.builder()
                .pedidos(List.of(primerPedido, segundoPedido))
                .build();

        assertEquals(new BigDecimal("40.95"), cuenta.calcularTotal());
    }

    @Test
    void cuentaSinPedidosRetornaCero() {
        assertEquals(BigDecimal.ZERO, new Cuenta().calcularTotal());
        assertEquals(BigDecimal.ZERO, Cuenta.builder().build().calcularTotal());
    }
}
