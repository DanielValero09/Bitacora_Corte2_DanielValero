package com.restaurante.service.auditoria;

import com.restaurante.model.domain.enums.EstadoPedido;

import java.time.LocalDateTime;

/** Datos inmutables del cambio; no transporta entidades JPA ni contratos HTTP. */
public record CambioEstadoPedidoAuditEvent(
        Long pedidoId,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        String usuarioResponsable,
        LocalDateTime fechaHora) {
}
