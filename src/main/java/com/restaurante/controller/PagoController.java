package com.restaurante.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;

import com.restaurante.mapper.PagoMapper;
import com.restaurante.model.dto.response.PagoResponse;
import com.restaurante.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@PreAuthorize("hasAnyRole('MESERO','GERENTE')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Pagos")
public class PagoController {
    private final PagoService service;
    private final PagoMapper mapper;

    @PostMapping("/api/v1/cuentas/{cuentaId}/pago")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Registrar pago de una cuenta",
            description = "Registra el pago por el total congelado de la cuenta y la cierra.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pago registrado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Cuenta cerrada o con pago ya registrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PagoResponse registrarPago(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.registrarPago(cuentaId));
    }

    @GetMapping("/api/v1/cuentas/{cuentaId}/pago")
    @Operation(
            summary = "Consultar pago por cuenta",
            description = "Obtiene el pago registrado para una cuenta existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Cuenta o pago no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PagoResponse obtenerPorCuenta(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.obtenerPorCuenta(cuentaId));
    }

    @GetMapping("/api/v1/pagos")
    @Operation(
            summary = "Listar pagos",
            description = "Consulta todos los pagos registrados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagos consultados correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PagoResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/api/v1/pagos/{id}")
    @Operation(
            summary = "Consultar pago por identificador",
            description = "Obtiene el detalle de un pago existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pago encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pago no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PagoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }
}
