package com.restaurante.service;

import com.restaurante.model.domain.Pedido;

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
}
