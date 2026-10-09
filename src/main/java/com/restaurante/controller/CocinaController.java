package com.restaurante.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;

import com.restaurante.mapper.PedidoMapper;
import com.restaurante.model.dto.response.PedidoResponse;
import com.restaurante.service.PedidoService;
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
@PreAuthorize("hasAnyRole('COCINERO','GERENTE')")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/cocina")
@RequiredArgsConstructor
@Tag(name = "Cocina")
public class CocinaController {
    private final PedidoService pedidoService;
    private final PedidoMapper pedidoMapper;

    @GetMapping("/pedidos")
    @Operation(
            summary = "Consultar pedidos para cocina",
            description = "Lista los pedidos confirmados que todavía no han sido entregados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos de cocina consultados correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listarParaCocina().stream()
                .map(pedidoMapper::toResponse)
                .toList();
    }
}
