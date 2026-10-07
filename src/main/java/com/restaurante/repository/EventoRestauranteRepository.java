package com.restaurante.repository;

import com.restaurante.model.document.EventoRestauranteDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EventoRestauranteRepository
        extends MongoRepository<EventoRestauranteDocument, String> {
    List<EventoRestauranteDocument> findByEntidadTipoAndEntidadIdOrderByTimestampAsc(
            String entidadTipo, Long entidadId);
}
