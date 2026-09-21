package com.restaurante.mapper;

import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.dto.response.CuentaResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CuentaMapperTest {
    @Test
    void totalDelResponseProvieneDelCalculoDeLaCuenta() {
        Pedido pedido = Pedido.builder()
                .items(List.of(ItemPedido.builder()
                        .precioCongelado(new BigDecimal("12.50"))
                        .cantidad(2)
                        .build()))
                .build();
        Cuenta cuenta = Cuenta.builder().id(1L).mesaId(2L).pedidos(List.of(pedido)).build();

        CuentaResponse response = Mappers.getMapper(CuentaMapper.class).toResponse(cuenta);

        assertEquals(cuenta.calcularTotal(), response.total());
        assertEquals(new BigDecimal("25.00"), response.total());
    }
}
