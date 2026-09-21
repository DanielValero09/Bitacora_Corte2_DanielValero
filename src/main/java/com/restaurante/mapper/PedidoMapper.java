package com.restaurante.mapper;

import com.restaurante.model.domain.Pedido;
import com.restaurante.model.dto.response.PedidoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = ItemPedidoMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PedidoMapper {
    @Mapping(target = "total", expression = "java(pedido.calcularTotal())")
    PedidoResponse toResponse(Pedido pedido);
}
