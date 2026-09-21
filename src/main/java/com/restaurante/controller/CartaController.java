package com.restaurante.controller;

import com.restaurante.mapper.PlatoMapper;
import com.restaurante.model.dto.response.PlatoResponse;
import com.restaurante.service.PlatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carta")
@RequiredArgsConstructor
@Tag(name = "Carta digital")
public class CartaController {
    private final PlatoService service;
    private final PlatoMapper mapper;

    @GetMapping
    @Operation(summary = "Consultar platos activos, incluidos los no disponibles")
    public List<PlatoResponse> listar() {
        return service.listarCarta().stream().map(mapper::toResponse).toList();
    }
}
