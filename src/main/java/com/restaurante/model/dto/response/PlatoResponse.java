package com.restaurante.model.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PlatoResponse(Long id, String nombre, String descripcion, BigDecimal precio,
                            boolean activo, boolean combo, boolean disponible,
                            List<IngredienteResponse> ingredientes) {
}
