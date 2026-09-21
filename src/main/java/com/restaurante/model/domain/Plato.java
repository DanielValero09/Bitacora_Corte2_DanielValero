package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plato {

    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private boolean activo;
    private boolean combo;

    @Builder.Default
    private List<Ingrediente> ingredientes = new ArrayList<>();

    public boolean isDisponible() {
        return activo && ingredientes.stream().allMatch(Ingrediente::isDisponible);
    }
}

