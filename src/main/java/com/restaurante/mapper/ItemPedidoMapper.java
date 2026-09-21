package com.restaurante.mapper;

import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.dto.response.ItemPedidoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ItemPedidoMapper {
    @Mapping(target = "subtotal", expression = "java(item.calcularSubtotal())")
    ItemPedidoResponse toResponse(ItemPedido item);
}
