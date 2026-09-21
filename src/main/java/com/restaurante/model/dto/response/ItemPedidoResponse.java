package com.restaurante.model.dto.response;

import java.math.BigDecimal;

public record ItemPedidoResponse(
        Long id,
        Long platoId,
        String nombrePlato,
        BigDecimal precioCongelado,
        int cantidad,
        boolean combo,
        boolean bebidaIncluida,
        BigDecimal subtotal) {
}
