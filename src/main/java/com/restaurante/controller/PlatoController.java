package com.restaurante.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;

import com.restaurante.mapper.PlatoMapper;
import com.restaurante.model.dto.request.ActualizarPlatoRequest;
import com.restaurante.model.dto.request.CrearPlatoRequest;
import com.restaurante.model.dto.response.PlatoResponse;
import com.restaurante.service.PlatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("hasRole('GERENTE')")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/platos")
@RequiredArgsConstructor
@Tag(name = "Platos")
public class PlatoController {
    private final PlatoService service;
    private final PlatoMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear plato",
            description = "Registra un plato y lo asocia con los ingredientes indicados.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plato creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos del plato inválidos"),
            @ApiResponse(responseCode = "404", description = "Algún ingrediente no fue encontrado"),
            @ApiResponse(responseCode = "409", description = "Ya existe un plato con el mismo nombre"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PlatoResponse crear(@Valid @RequestBody CrearPlatoRequest request) {
        return mapper.toResponse(service.crear(mapper.toDomain(request), request.ingredienteIds()));
    }

    @GetMapping
    @Operation(
            summary = "Listar platos administrativos",
            description = "Consulta todos los platos, tanto activos como inactivos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Platos consultados correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PlatoResponse> listar() {
        return service.listarTodos().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar plato por identificador",
            description = "Obtiene el detalle administrativo de un plato existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plato encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Plato no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PlatoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar plato",
            description = "Reemplaza los datos editables y los ingredientes de un plato existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plato actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador o datos del plato inválidos"),
            @ApiResponse(responseCode = "404", description = "Plato o ingrediente no encontrado"),
            @ApiResponse(responseCode = "409", description = "Ya existe otro plato con el mismo nombre"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PlatoResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarPlatoRequest request) {
        return mapper.toResponse(service.actualizar(id, mapper.toDomain(request), request.ingredienteIds()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Desactivar plato",
            description = "Desactiva lógicamente un plato sin eliminarlo del catálogo administrativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Plato desactivado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Plato no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public void desactivar(@PathVariable Long id) {
        service.desactivar(id);
    }
}
