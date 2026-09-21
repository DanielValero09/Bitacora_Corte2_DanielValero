package com.restaurante.controller;

import com.restaurante.mapper.PagoMapper;
import com.restaurante.model.dto.response.PagoResponse;
import com.restaurante.service.PagoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Pagos")
public class PagoController {
    private final PagoService service;
    private final PagoMapper mapper;

    @PostMapping("/api/v1/cuentas/{cuentaId}/pago")
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponse registrarPago(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.registrarPago(cuentaId));
    }

    @GetMapping("/api/v1/cuentas/{cuentaId}/pago")
    public PagoResponse obtenerPorCuenta(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.obtenerPorCuenta(cuentaId));
    }

    @GetMapping("/api/v1/pagos")
    public List<PagoResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/api/v1/pagos/{id}")
    public PagoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }
}
