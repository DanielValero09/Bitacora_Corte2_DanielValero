package com.restaurante.mapper;

import com.restaurante.model.domain.Mesa;
import com.restaurante.model.entity.MesaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MesaEntityMapper {

    MesaEntity toEntity(Mesa mesa);

    Mesa toDomain(MesaEntity entity);
}
