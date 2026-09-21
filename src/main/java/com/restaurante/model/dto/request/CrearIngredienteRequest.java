package com.restaurante.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearIngredienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull Boolean disponible) {
}
