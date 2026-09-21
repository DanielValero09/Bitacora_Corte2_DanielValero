package com.restaurante.service;

import com.restaurante.model.domain.Pago;

import java.util.List;

public interface PagoService {
    Pago registrarPago(Long cuentaId);

    Pago obtenerPorId(Long pagoId);

    Pago obtenerPorCuenta(Long cuentaId);

    List<Pago> listar();
}
