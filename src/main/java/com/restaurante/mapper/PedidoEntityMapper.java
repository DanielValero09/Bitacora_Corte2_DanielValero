package com.restaurante.mapper;

import com.restaurante.model.domain.Pedido;
import com.restaurante.model.entity.CambioEstadoPedidoEntity;
import com.restaurante.model.entity.ItemPedidoEntity;
import com.restaurante.model.entity.PedidoEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        uses = {ItemPedidoEntityMapper.class, CambioEstadoPedidoEntityMapper.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PedidoEntityMapper {

    @Mapping(target = "cuenta", ignore = true)
    PedidoEntity toEntity(Pedido pedido);

    @Mapping(target = "cuentaId", source = "cuenta.id")
    Pedido toDomain(PedidoEntity entity);

    @AfterMapping
    default void vincularHijos(@MappingTarget PedidoEntity pedido) {
        for (ItemPedidoEntity item : pedido.getItems()) {
            item.setPedido(pedido);
        }
        for (CambioEstadoPedidoEntity cambio : pedido.getHistorialEstados()) {
            cambio.setPedido(pedido);
        }
    }
}
