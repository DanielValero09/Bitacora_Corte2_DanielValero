package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido {

    private Long id;
    private Long platoId;
    private String nombrePlato;
    private BigDecimal precioCongelado;
    private int cantidad;
    private boolean combo;
    private boolean bebidaIncluida;

    public BigDecimal calcularSubtotal() {
        return precioCongelado.multiply(BigDecimal.valueOf(cantidad));
    }
}

