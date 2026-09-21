package com.restaurante.controller;

import com.restaurante.mapper.IngredienteMapper;
import com.restaurante.model.dto.request.CambiarDisponibilidadIngredienteRequest;
import com.restaurante.model.dto.request.CrearIngredienteRequest;
import com.restaurante.model.dto.response.IngredienteResponse;
import com.restaurante.service.IngredienteService;
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
    public IngredienteResponse crear(@Valid @RequestBody CrearIngredienteRequest request) {
        return mapper.toResponse(service.crear(mapper.toDomain(request)));
    }

    @GetMapping
    public List<IngredienteResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public IngredienteResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @PatchMapping("/{id}/disponibilidad")
    public IngredienteResponse cambiarDisponibilidad(@PathVariable Long id,
            @Valid @RequestBody CambiarDisponibilidadIngredienteRequest request) {
        return mapper.toResponse(service.cambiarDisponibilidad(id, request.disponible()));
    }
}
