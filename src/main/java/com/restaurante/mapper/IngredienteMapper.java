package com.restaurante.mapper;

import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.dto.request.CrearIngredienteRequest;
import com.restaurante.model.dto.response.IngredienteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IngredienteMapper {
    @Mapping(target = "id", ignore = true)
    Ingrediente toDomain(CrearIngredienteRequest request);

    IngredienteResponse toResponse(Ingrediente ingrediente);
}
