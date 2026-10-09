package com.restaurante.mapper;

import com.restaurante.model.document.EventoRestauranteDocument;
import com.restaurante.model.domain.EventoRestaurante;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventoRestauranteMapper {
    @Mapping(target = "id", ignore = true)
    EventoRestauranteDocument toDocument(EventoRestaurante evento);

    EventoRestaurante toDomain(EventoRestauranteDocument document);
}
