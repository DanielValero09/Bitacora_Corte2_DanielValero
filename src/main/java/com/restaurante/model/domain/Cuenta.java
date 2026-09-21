package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.restaurante.model.domain.enums.EstadoCuenta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cuenta {

    private Long id;
    private Long mesaId;

    @Builder.Default
    private EstadoCuenta estado = EstadoCuenta.ABIERTA;

    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;

    @Builder.Default
    private List<Pedido> pedidos = new ArrayList<>();

    public BigDecimal calcularTotal() {
        return pedidos.stream()
                .map(Pedido::calcularTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

