package com.restaurante.mapper;

import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.dto.response.ItemPedidoResponse;
import com.restaurante.model.dto.response.PedidoResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PedidoMapperTest {
    @Test
    void itemMapperDerivaSubtotalDelDominio() {
        ItemPedido item = ItemPedido.builder()
                .precioCongelado(new BigDecimal("12.50"))
                .cantidad(3)
                .build();

        ItemPedidoResponse response = Mappers.getMapper(ItemPedidoMapper.class).toResponse(item);

        assertEquals(item.calcularSubtotal(), response.subtotal());
        assertEquals(new BigDecimal("37.50"), response.subtotal());
    }

    @Test
    void pedidoMapperDerivaTotalYMapeaItems() {
        ItemPedido item = ItemPedido.builder()
                .id(2L)
                .precioCongelado(new BigDecimal("10.00"))
                .cantidad(2)
                .build();
        Pedido pedido = Pedido.builder().id(1L).items(List.of(item)).build();
        PedidoMapper mapper = Mappers.getMapper(PedidoMapper.class);
        ReflectionTestUtils.setField(mapper, "itemPedidoMapper",
                Mappers.getMapper(ItemPedidoMapper.class));

        PedidoResponse response = mapper.toResponse(pedido);

        assertEquals(pedido.calcularTotal(), response.total());
        assertEquals(new BigDecimal("20.00"), response.total());
        assertEquals(item.calcularSubtotal(), response.items().getFirst().subtotal());
    }
}
