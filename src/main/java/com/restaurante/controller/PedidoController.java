package com.restaurante.controller;

import com.restaurante.mapper.PedidoMapper;
import com.restaurante.model.dto.request.ActualizarCantidadItemRequest;
import com.restaurante.model.dto.request.AgregarItemPedidoRequest;
import com.restaurante.model.dto.response.PedidoResponse;
import com.restaurante.service.PedidoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Pedidos")
public class PedidoController {
    private final PedidoService service;
    private final PedidoMapper mapper;

    @PostMapping("/api/v1/cuentas/{cuentaId}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.crear(cuentaId));
    }

    @GetMapping("/api/v1/pedidos")
    public List<PedidoResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/api/v1/pedidos/{id}")
    public PedidoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @GetMapping("/api/v1/cuentas/{cuentaId}/pedidos")
    public List<PedidoResponse> listarPorCuenta(@PathVariable Long cuentaId) {
        return service.listarPorCuenta(cuentaId).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/items")
    public PedidoResponse agregarItem(@PathVariable Long pedidoId,
            @Valid @RequestBody AgregarItemPedidoRequest request) {
        return mapper.toResponse(service.agregarItem(pedidoId, request.platoId(), request.cantidad()));
    }

    @PatchMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}")
    public PedidoResponse actualizarCantidad(@PathVariable Long pedidoId, @PathVariable Long itemId,
            @Valid @RequestBody ActualizarCantidadItemRequest request) {
        return mapper.toResponse(service.actualizarCantidadItem(pedidoId, itemId, request.cantidad()));
    }

    @DeleteMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarItem(@PathVariable Long pedidoId, @PathVariable Long itemId) {
        service.eliminarItem(pedidoId, itemId);
    }

    @PatchMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}/bebida")
    public PedidoResponse retirarBebida(@PathVariable Long pedidoId, @PathVariable Long itemId) {
        return mapper.toResponse(service.retirarBebidaCombo(pedidoId, itemId));
    }
}
