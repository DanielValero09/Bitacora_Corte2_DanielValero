package com.restaurante.model.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PedidoTest {

    @Test
    void calcularTotalSumaLosSubtotalesDeTodosLosItems() {
        Pedido pedido = Pedido.builder()
                .items(List.of(
                        ItemPedido.builder().precioCongelado(new BigDecimal("10.50")).cantidad(2).build(),
                        ItemPedido.builder().precioCongelado(new BigDecimal("3.25")).cantidad(3).build()))
                .build();

        assertEquals(new BigDecimal("30.75"), pedido.calcularTotal());
    }

    @Test
    void pedidoSinItemsRetornaCero() {
        assertEquals(BigDecimal.ZERO, new Pedido().calcularTotal());
        assertEquals(BigDecimal.ZERO, Pedido.builder().build().calcularTotal());
    }
}
