package com.restaurante.model.dto.response;

import com.restaurante.model.domain.enums.EstadoCuenta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CuentaResponse(
        Long id,
        Long mesaId,
        EstadoCuenta estado,
        LocalDateTime fechaApertura,
        LocalDateTime fechaCierre,
        BigDecimal total) {
}
