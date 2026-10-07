package com.restaurante.model.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "eventos_restaurante")
public class EventoRestauranteDocument {
    @Id
    private String id;
    private String tipo;
    private String entidadTipo;
    private Long entidadId;
    private String descripcion;
    private String usuario;
    private LocalDateTime timestamp;
    private Map<String, Object> metadatos;
}
