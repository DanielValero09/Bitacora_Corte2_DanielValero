package com.restaurante.mapper;

import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.entity.CambioEstadoPedidoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CambioEstadoPedidoEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pedido", ignore = true)
    CambioEstadoPedidoEntity toEntity(CambioEstadoPedido cambio);

    CambioEstadoPedido toDomain(CambioEstadoPedidoEntity entity);
}
