package com.restaurante.model.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemPedidoTest {

    @Test
    void calcularSubtotalMultiplicaElPrecioCongeladoPorLaCantidad() {
        ItemPedido item = ItemPedido.builder()
                .precioCongelado(new BigDecimal("10.50"))
                .cantidad(3)
                .build();

        assertEquals(new BigDecimal("31.50"), item.calcularSubtotal());
    }

    @Test
    void calcularSubtotalConservaElPrecioAunqueCambieElPlato() {
        Plato plato = Plato.builder()
                .id(1L)
                .nombre("Hamburguesa")
                .precio(new BigDecimal("10.50"))
                .build();
        ItemPedido item = ItemPedido.builder()
                .platoId(plato.getId())
                .nombrePlato(plato.getNombre())
                .precioCongelado(plato.getPrecio())
                .cantidad(2)
                .build();

        plato.setPrecio(new BigDecimal("15.00"));

        assertEquals(new BigDecimal("21.00"), item.calcularSubtotal());
    }

    @Test
    void calcularSubtotalDeComboNoDependeDeLaBebidaIncluida() {
        ItemPedido item = ItemPedido.builder()
                .precioCongelado(new BigDecimal("18.50"))
                .cantidad(2)
                .combo(true)
                .bebidaIncluida(true)
                .build();

        assertEquals(new BigDecimal("37.00"), item.calcularSubtotal());

        item.setBebidaIncluida(false);

        assertEquals(new BigDecimal("37.00"), item.calcularSubtotal());
    }
}
