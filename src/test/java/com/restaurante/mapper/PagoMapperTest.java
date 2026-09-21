package com.restaurante.mapper;

import com.restaurante.model.domain.Pago;
import com.restaurante.model.dto.response.PagoResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagoMapperTest {
    private final PagoMapper mapper = Mappers.getMapper(PagoMapper.class);

    @Test
    void conviertePagoAResponse() {
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 21, 12, 30);
        Pago pago = Pago.builder()
                .id(5L)
                .cuentaId(3L)
                .monto(new BigDecimal("20.00"))
                .fechaHora(fecha)
                .build();

        PagoResponse response = mapper.toResponse(pago);

        assertEquals(5L, response.id());
        assertEquals(3L, response.cuentaId());
        assertEquals(new BigDecimal("20.00"), response.monto());
        assertEquals(fecha, response.fechaHora());
    }
}
