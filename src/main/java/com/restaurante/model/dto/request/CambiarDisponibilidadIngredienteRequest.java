package com.restaurante.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record CambiarDisponibilidadIngredienteRequest(@NotNull Boolean disponible) {
}
