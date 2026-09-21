package com.restaurante.mapper;

import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.dto.response.CambioEstadoPedidoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CambioEstadoPedidoMapper {
    CambioEstadoPedidoResponse toResponse(CambioEstadoPedido cambio);
}
