package com.restaurante.service;

import com.restaurante.model.domain.Cuenta;

import java.util.List;

public interface CuentaService {
    Cuenta abrirCuenta(Long mesaId);

    Cuenta obtenerPorId(Long cuentaId);

    Cuenta obtenerCuentaAbiertaPorMesa(Long mesaId);

    List<Cuenta> listar();
}
