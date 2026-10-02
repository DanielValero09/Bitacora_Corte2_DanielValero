package com.restaurante.mapper;

import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.PedidoEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = PedidoEntityMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CuentaEntityMapper {

    @Mapping(target = "mesa", ignore = true)
    @Mapping(target = "cuentaAbierta", ignore = true)
    CuentaEntity toEntity(Cuenta cuenta);

    @Mapping(target = "mesaId", source = "mesa.id")
    Cuenta toDomain(CuentaEntity entity);

    @AfterMapping
    default void vincularPedidos(@MappingTarget CuentaEntity cuenta) {
        for (PedidoEntity pedido : cuenta.getPedidos()) {
            pedido.setCuenta(cuenta);
        }
    }
}
