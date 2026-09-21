package com.restaurante.model.dto.request;

import com.restaurante.model.domain.enums.EstadoPedido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambiarEstadoPedidoRequest(
        @NotNull EstadoPedido nuevoEstado,
        @NotBlank @Size(max = 100) String usuarioResponsable) {
}
