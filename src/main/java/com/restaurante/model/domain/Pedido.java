package com.restaurante.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.restaurante.model.domain.enums.EstadoPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    private Long id;
    private Long cuentaId;

    @Builder.Default
    private List<ItemPedido> items = new ArrayList<>();

    @Builder.Default
    private EstadoPedido estado = EstadoPedido.RECIBIDO;

    private boolean confirmado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaConfirmacion;

    @Builder.Default
    private List<CambioEstadoPedido> historialEstados = new ArrayList<>();

    public BigDecimal calcularTotal() {
        return items.stream()
                .map(ItemPedido::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

