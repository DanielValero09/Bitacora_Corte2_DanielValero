package com.restaurante.mapper;

import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.model.dto.response.CambioEstadoPedidoResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CambioEstadoPedidoMapperTest {
    @Test
    void mapeaTodosLosCamposSinLogicaAdicional() {
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 21, 10, 30);
        CambioEstadoPedido cambio = CambioEstadoPedido.builder()
                .estadoAnterior(EstadoPedido.RECIBIDO)
                .estadoNuevo(EstadoPedido.EN_PREPARACION)
                .usuarioResponsable("cocinero")
                .fechaHora(fecha)
                .build();

        CambioEstadoPedidoResponse response = Mappers.getMapper(CambioEstadoPedidoMapper.class)
                .toResponse(cambio);

        assertEquals(cambio.getEstadoAnterior(), response.estadoAnterior());
        assertEquals(cambio.getEstadoNuevo(), response.estadoNuevo());
        assertEquals(cambio.getUsuarioResponsable(), response.usuarioResponsable());
        assertEquals(cambio.getFechaHora(), response.fechaHora());
    }

    @Test
    void entradaNulaProduceRespuestaNula() {
        assertNull(Mappers.getMapper(CambioEstadoPedidoMapper.class).toResponse(null));
    }
}
