package com.restaurante.mapper;

import com.restaurante.model.domain.Plato;
import com.restaurante.model.entity.PlatoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = IngredienteEntityMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlatoEntityMapper {

    PlatoEntity toEntity(Plato plato);

    Plato toDomain(PlatoEntity entity);
}
