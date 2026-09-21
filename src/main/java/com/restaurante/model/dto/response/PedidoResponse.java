package com.restaurante.model.dto.response;

import com.restaurante.model.domain.enums.EstadoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        Long cuentaId,
        EstadoPedido estado,
        boolean confirmado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaConfirmacion,
        List<ItemPedidoResponse> items,
        BigDecimal total) {
}
