package com.restaurante.mapper;

import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.entity.ItemPedidoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ItemPedidoEntityMapper {

    @Mapping(target = "pedido", ignore = true)
    ItemPedidoEntity toEntity(ItemPedido item);

    ItemPedido toDomain(ItemPedidoEntity entity);
}
