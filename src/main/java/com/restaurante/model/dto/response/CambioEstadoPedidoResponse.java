package com.restaurante.model.dto.response;

import com.restaurante.model.domain.enums.EstadoPedido;

import java.time.LocalDateTime;

public record CambioEstadoPedidoResponse(
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        String usuarioResponsable,
        LocalDateTime fechaHora) {
}
