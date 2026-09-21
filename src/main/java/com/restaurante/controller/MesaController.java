package com.restaurante.controller;

import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.MesaMapper;
import com.restaurante.model.dto.request.CrearMesaRequest;
import com.restaurante.model.dto.response.CuentaResponse;
import com.restaurante.model.dto.response.MesaResponse;
import com.restaurante.service.CuentaService;
import com.restaurante.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
@RequiredArgsConstructor
@Tag(name = "Mesas")
public class MesaController {
    private final MesaService mesaService;
    private final MesaMapper mesaMapper;
    private final CuentaService cuentaService;
    private final CuentaMapper cuentaMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponse crear(@Valid @RequestBody CrearMesaRequest request) {
        return mesaMapper.toResponse(mesaService.crear(mesaMapper.toDomain(request)));
    }

    @GetMapping
    public List<MesaResponse> listar() {
        return mesaService.listar().stream().map(mesaMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MesaResponse obtenerPorId(@PathVariable Long id) {
        return mesaMapper.toResponse(mesaService.obtenerPorId(id));
    }

    @PostMapping("/{mesaId}/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir una cuenta para una mesa")
    public CuentaResponse abrirCuenta(@PathVariable Long mesaId) {
        return cuentaMapper.toResponse(cuentaService.abrirCuenta(mesaId));
    }

    @GetMapping("/{mesaId}/cuenta-abierta")
    @Operation(summary = "Consultar la cuenta abierta de una mesa")
    public CuentaResponse obtenerCuentaAbierta(@PathVariable Long mesaId) {
        return cuentaMapper.toResponse(cuentaService.obtenerCuentaAbiertaPorMesa(mesaId));
    }
}
