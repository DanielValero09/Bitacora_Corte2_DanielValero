package com.restaurante.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CrearPlatoRequest(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 1000) String descripcion,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precio,
        @NotNull Boolean combo,
        @NotNull List<@NotNull @Positive Long> ingredienteIds) {
}
