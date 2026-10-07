package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoRestaurante {
    private String id;
    private String tipo;
    private String entidadTipo;
    private Long entidadId;
    private String descripcion;
    private String usuario;
    private LocalDateTime timestamp;
    private Map<String, Object> metadatos;
}
