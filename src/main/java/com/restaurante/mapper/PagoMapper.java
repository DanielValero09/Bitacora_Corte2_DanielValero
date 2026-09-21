package com.restaurante.mapper;

import com.restaurante.model.domain.Pago;
import com.restaurante.model.dto.response.PagoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PagoMapper {
    PagoResponse toResponse(Pago pago);
}
