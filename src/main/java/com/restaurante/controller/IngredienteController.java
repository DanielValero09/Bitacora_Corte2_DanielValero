package com.restaurante.controller;

import com.restaurante.mapper.IngredienteMapper;
import com.restaurante.model.dto.request.CambiarDisponibilidadIngredienteRequest;
import com.restaurante.model.dto.request.CrearIngredienteRequest;
import com.restaurante.model.dto.response.IngredienteResponse;
import com.restaurante.service.IngredienteService;
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
@RequestMapping("/api/v1/ingredientes")
@RequiredArgsConstructor
@Tag(name = "Ingredientes")
public class IngredienteController {
    private final IngredienteService service;
    private final IngredienteMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear ingrediente",
            description = "Registra un ingrediente con su disponibilidad inicial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ingrediente creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos del ingrediente inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe un ingrediente con el mismo nombre"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public IngredienteResponse crear(@Valid @RequestBody CrearIngredienteRequest request) {
        return mapper.toResponse(service.crear(mapper.toDomain(request)));
    }

    @GetMapping
    @Operation(
            summary = "Listar ingredientes",
            description = "Consulta todos los ingredientes registrados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingredientes consultados correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<IngredienteResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar ingrediente por identificador",
            description = "Obtiene el detalle de un ingrediente existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ingrediente encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Ingrediente no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public IngredienteResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @PatchMapping("/{id}/disponibilidad")
    @Operation(
            summary = "Cambiar disponibilidad de ingrediente",
            description = "Actualiza la disponibilidad de un ingrediente existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Disponibilidad actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador o cuerpo de solicitud inválido"),
            @ApiResponse(responseCode = "404", description = "Ingrediente no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public IngredienteResponse cambiarDisponibilidad(@PathVariable Long id,
            @Valid @RequestBody CambiarDisponibilidadIngredienteRequest request) {
        return mapper.toResponse(service.cambiarDisponibilidad(id, request.disponible()));
    }
}
