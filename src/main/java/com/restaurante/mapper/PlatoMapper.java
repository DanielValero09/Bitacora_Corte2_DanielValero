package com.restaurante.mapper;

import com.restaurante.model.domain.Plato;
import com.restaurante.model.dto.request.ActualizarPlatoRequest;
import com.restaurante.model.dto.request.CrearPlatoRequest;
import com.restaurante.model.dto.response.PlatoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = IngredienteMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PlatoMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "ingredientes", ignore = true)
    Plato toDomain(CrearPlatoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "ingredientes", ignore = true)
    Plato toDomain(ActualizarPlatoRequest request);

    @Mapping(target = "disponible", source = "disponible")
    PlatoResponse toResponse(Plato plato);
}
