package com.restaurante.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.restaurante.mapper.PlatoMapper;
import com.restaurante.model.dto.response.PlatoResponse;
import com.restaurante.service.PlatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/api/v1/carta")
@RequiredArgsConstructor
@Tag(name = "Carta digital")
public class CartaController {
    private final PlatoService service;
    private final PlatoMapper mapper;

    @GetMapping
    @Operation(
            summary = "Consultar la carta digital",
            description = "Lista los platos activos, incluidos los que temporalmente no están disponibles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Carta consultada correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PlatoResponse> listar() {
        return service.listarCarta().stream().map(mapper::toResponse).toList();
    }
}
