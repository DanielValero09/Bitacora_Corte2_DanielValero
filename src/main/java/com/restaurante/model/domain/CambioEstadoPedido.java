package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.restaurante.model.domain.enums.EstadoPedido;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambioEstadoPedido {

    private EstadoPedido estadoAnterior;
    private EstadoPedido estadoNuevo;
    private String usuarioResponsable;
    private LocalDateTime fechaHora;
}

