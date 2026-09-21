package com.restaurante.controller;

import com.restaurante.mapper.PedidoMapper;
import com.restaurante.model.dto.response.PedidoResponse;
import com.restaurante.service.PedidoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cocina")
@RequiredArgsConstructor
@Tag(name = "Cocina")
public class CocinaController {
    private final PedidoService pedidoService;
    private final PedidoMapper pedidoMapper;

    @GetMapping("/pedidos")
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listarParaCocina().stream()
                .map(pedidoMapper::toResponse)
                .toList();
    }
}
