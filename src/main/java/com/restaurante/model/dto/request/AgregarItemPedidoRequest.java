package com.restaurante.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AgregarItemPedidoRequest(
        @NotNull @Positive Long platoId,
        @NotNull @Positive Integer cantidad) {
}
