package com.restaurante.service;

import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.enums.EstadoPedido;

import java.util.List;

public interface PedidoService {
    Pedido crear(Long cuentaId);

    Pedido obtenerPorId(Long pedidoId);

    List<Pedido> listar();

    List<Pedido> listarPorCuenta(Long cuentaId);

    Pedido agregarItem(Long pedidoId, Long platoId, int cantidad);

    Pedido actualizarCantidadItem(Long pedidoId, Long itemId, int cantidad);

    void eliminarItem(Long pedidoId, Long itemId);

    Pedido retirarBebidaCombo(Long pedidoId, Long itemId);

    Pedido confirmar(Long pedidoId);

    List<Pedido> listarParaCocina();

    Pedido cambiarEstado(Long pedidoId, EstadoPedido nuevoEstado, String usuarioResponsable);

    List<CambioEstadoPedido> obtenerHistorial(Long pedidoId);
}
