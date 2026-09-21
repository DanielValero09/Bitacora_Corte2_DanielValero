package com.restaurante.controller;

import com.restaurante.mapper.PlatoMapper;
import com.restaurante.model.dto.request.ActualizarPlatoRequest;
import com.restaurante.model.dto.request.CrearPlatoRequest;
import com.restaurante.model.dto.response.PlatoResponse;
import com.restaurante.service.PlatoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platos")
@RequiredArgsConstructor
@Tag(name = "Platos")
public class PlatoController {
    private final PlatoService service;
    private final PlatoMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatoResponse crear(@Valid @RequestBody CrearPlatoRequest request) {
        return mapper.toResponse(service.crear(mapper.toDomain(request), request.ingredienteIds()));
    }

    @GetMapping
    public List<PlatoResponse> listar() {
        return service.listarTodos().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PlatoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public PlatoResponse actualizar(@PathVariable Long id,
            @Valid @RequestBody ActualizarPlatoRequest request) {
        return mapper.toResponse(service.actualizar(id, mapper.toDomain(request), request.ingredienteIds()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Long id) {
        service.desactivar(id);
    }
}
