package com.restaurante.mapper;

import com.restaurante.model.domain.Pago;
import com.restaurante.model.entity.PagoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PagoEntityMapper {

    @Mapping(target = "cuenta", ignore = true)
    PagoEntity toEntity(Pago pago);

    @Mapping(target = "cuentaId", source = "cuenta.id")
    Pago toDomain(PagoEntity entity);
}
