package com.restaurante.mapper;

import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.entity.IngredienteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IngredienteEntityMapper {

    IngredienteEntity toEntity(Ingrediente ingrediente);

    Ingrediente toDomain(IngredienteEntity entity);
}
