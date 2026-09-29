package com.restaurante.controller;

import com.restaurante.mapper.CuentaMapper;
import com.restaurante.mapper.MesaMapper;
import com.restaurante.model.dto.request.CrearMesaRequest;
import com.restaurante.model.dto.response.CuentaResponse;
import com.restaurante.model.dto.response.MesaResponse;
import com.restaurante.service.CuentaService;
import com.restaurante.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(
            summary = "Crear mesa",
            description = "Registra una mesa con un número único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mesa creada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de la mesa inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una mesa con el mismo número"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public MesaResponse crear(@Valid @RequestBody CrearMesaRequest request) {
        return mesaMapper.toResponse(mesaService.crear(mesaMapper.toDomain(request)));
    }

    @GetMapping
    @Operation(
            summary = "Listar mesas",
            description = "Consulta todas las mesas registradas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesas consultadas correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<MesaResponse> listar() {
        return mesaService.listar().stream().map(mesaMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar mesa por identificador",
            description = "Obtiene el detalle de una mesa existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesa encontrada"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Mesa no encontrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public MesaResponse obtenerPorId(@PathVariable Long id) {
        return mesaMapper.toResponse(mesaService.obtenerPorId(id));
    }

    @PostMapping("/{mesaId}/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Abrir una cuenta para una mesa",
            description = "Crea una cuenta abierta para una mesa que todavía no tenga otra cuenta abierta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta abierta correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Mesa no encontrada"),
            @ApiResponse(responseCode = "409", description = "La mesa ya tiene una cuenta abierta"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public CuentaResponse abrirCuenta(@PathVariable Long mesaId) {
        return cuentaMapper.toResponse(cuentaService.abrirCuenta(mesaId));
    }

    @GetMapping("/{mesaId}/cuenta-abierta")
    @Operation(
            summary = "Consultar la cuenta abierta de una mesa",
            description = "Obtiene la cuenta abierta asociada a una mesa existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta abierta encontrada"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Mesa o cuenta abierta no encontrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public CuentaResponse obtenerCuentaAbierta(@PathVariable Long mesaId) {
        return cuentaMapper.toResponse(cuentaService.obtenerCuentaAbiertaPorMesa(mesaId));
    }
}
