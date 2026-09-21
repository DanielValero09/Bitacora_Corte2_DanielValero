package com.restaurante.mapper;

import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.dto.response.CuentaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CuentaMapper {
    @Mapping(target = "total", expression = "java(cuenta.calcularTotal())")
    CuentaResponse toResponse(Cuenta cuenta);
}
