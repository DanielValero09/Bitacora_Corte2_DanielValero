package com.restaurante.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearMesaRequest(
        @NotNull @Positive Integer numero) {
}
