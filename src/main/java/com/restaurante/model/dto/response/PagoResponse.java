package com.restaurante.model.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoResponse(
        Long id,
        Long cuentaId,
        BigDecimal monto,
        LocalDateTime fechaHora) {
}
