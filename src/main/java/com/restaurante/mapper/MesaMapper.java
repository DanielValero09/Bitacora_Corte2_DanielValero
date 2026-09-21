package com.restaurante.mapper;

import com.restaurante.model.domain.Mesa;
import com.restaurante.model.dto.request.CrearMesaRequest;
import com.restaurante.model.dto.response.MesaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MesaMapper {
    @Mapping(target = "id", ignore = true)
    Mesa toDomain(CrearMesaRequest request);

    MesaResponse toResponse(Mesa mesa);
}
