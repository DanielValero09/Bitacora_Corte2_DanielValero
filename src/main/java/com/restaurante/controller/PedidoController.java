package com.restaurante.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;

import com.restaurante.mapper.CambioEstadoPedidoMapper;
import com.restaurante.mapper.PedidoMapper;
import com.restaurante.model.dto.request.CambiarEstadoPedidoRequest;
import com.restaurante.model.dto.request.ActualizarCantidadItemRequest;
import com.restaurante.model.dto.request.AgregarItemPedidoRequest;
import com.restaurante.model.dto.response.CambioEstadoPedidoResponse;
import com.restaurante.model.dto.response.PedidoResponse;
import com.restaurante.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@PreAuthorize("hasAnyRole('MESERO','GERENTE')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Pedidos")
public class PedidoController {
    private final PedidoService service;
    private final PedidoMapper mapper;
    private final CambioEstadoPedidoMapper cambioEstadoMapper;

    @PostMapping("/api/v1/cuentas/{cuentaId}/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear pedido para una cuenta",
            description = "Crea un pedido en estado RECIBIDO para una cuenta abierta.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada"),
            @ApiResponse(responseCode = "409", description = "La cuenta no está abierta"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse crear(@PathVariable Long cuentaId) {
        return mapper.toResponse(service.crear(cuentaId));
    }

    @GetMapping("/api/v1/pedidos")
    @Operation(
            summary = "Listar pedidos",
            description = "Consulta todos los pedidos registrados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos consultados correctamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PedidoResponse> listar() {
        return service.listar().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/api/v1/pedidos/{id}")
    @PreAuthorize("hasAnyRole('MESERO','COCINERO','GERENTE')")
    @Operation(
            summary = "Consultar pedido por identificador",
            description = "Obtiene el detalle de un pedido existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse obtenerPorId(@PathVariable Long id) {
        return mapper.toResponse(service.obtenerPorId(id));
    }

    @GetMapping("/api/v1/cuentas/{cuentaId}/pedidos")
    @Operation(
            summary = "Listar pedidos de una cuenta",
            description = "Consulta los pedidos asociados a una cuenta existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedidos de la cuenta consultados correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<PedidoResponse> listarPorCuenta(@PathVariable Long cuentaId) {
        return service.listarPorCuenta(cuentaId).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/items")
    @Operation(
            summary = "Agregar producto a un pedido",
            description = "Agrega un plato disponible al pedido y congela sus datos económicos actuales.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto agregado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador o datos del producto inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido, cuenta o plato no encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido no editable, cuenta cerrada o plato no disponible"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse agregarItem(@PathVariable Long pedidoId,
            @Valid @RequestBody AgregarItemPedidoRequest request) {
        return mapper.toResponse(service.agregarItem(pedidoId, request.platoId(), request.cantidad()));
    }

    @PatchMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}")
    @Operation(
            summary = "Actualizar cantidad de un producto",
            description = "Cambia la cantidad de un producto mientras el pedido sea editable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cantidad actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador o cantidad inválida"),
            @ApiResponse(responseCode = "404", description = "Pedido, cuenta o producto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido no editable o cuenta cerrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse actualizarCantidad(@PathVariable Long pedidoId, @PathVariable Long itemId,
            @Valid @RequestBody ActualizarCantidadItemRequest request) {
        return mapper.toResponse(service.actualizarCantidadItem(pedidoId, itemId, request.cantidad()));
    }

    @DeleteMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Eliminar producto de un pedido",
            description = "Retira un producto mientras el pedido sea editable.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pedido, cuenta o producto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido no editable o cuenta cerrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public void eliminarItem(@PathVariable Long pedidoId, @PathVariable Long itemId) {
        service.eliminarItem(pedidoId, itemId);
    }

    @PatchMapping("/api/v1/pedidos/{pedidoId}/items/{itemId}/bebida")
    @Operation(
            summary = "Retirar bebida de un combo",
            description = "Retira la bebida incluida sin modificar el precio congelado del combo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bebida retirada correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pedido, cuenta o producto no encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido no editable, cuenta cerrada o producto no es combo"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse retirarBebida(@PathVariable Long pedidoId, @PathVariable Long itemId) {
        return mapper.toResponse(service.retirarBebidaCombo(pedidoId, itemId));
    }

    @PostMapping("/api/v1/pedidos/{pedidoId}/confirmacion")
    @Operation(
            summary = "Confirmar pedido",
            description = "Confirma un pedido RECIBIDO con productos disponibles para hacerlo visible en cocina.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido confirmado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pedido, cuenta o plato no encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido no confirmable, cuenta cerrada o plato no disponible"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse confirmar(@PathVariable Long pedidoId) {
        return mapper.toResponse(service.confirmar(pedidoId));
    }

    @PatchMapping("/api/v1/pedidos/{pedidoId}/estado")
    @PreAuthorize(
            "hasAnyRole('MESERO','COCINERO','GERENTE') and (hasRole('GERENTE') "
            + "or #request.nuevoEstado() == null "
            + "or (hasRole('COCINERO') and (#request.nuevoEstado().name() == 'EN_PREPARACION' "
            + "or #request.nuevoEstado().name() == 'LISTO')) "
            + "or (hasRole('MESERO') and #request.nuevoEstado().name() == 'ENTREGADO'))")
    @Operation(
            summary = "Cambiar estado de un pedido",
            description = "Aplica la siguiente transición válida y registra usuario y fecha en el historial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador o datos de transición inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado"),
            @ApiResponse(responseCode = "409", description = "Transición inválida o pedido no confirmado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public PedidoResponse cambiarEstado(@PathVariable Long pedidoId,
            @Valid @RequestBody CambiarEstadoPedidoRequest request) {
        return mapper.toResponse(service.cambiarEstado(
                pedidoId, request.nuevoEstado(), request.usuarioResponsable()));
    }

    @GetMapping("/api/v1/pedidos/{pedidoId}/historial")
    @PreAuthorize("hasAnyRole('MESERO','COCINERO','GERENTE')")
    @Operation(
            summary = "Consultar historial de estados",
            description = "Lista en orden los cambios de estado registrados para un pedido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial consultado correctamente"),
            @ApiResponse(responseCode = "400", description = "Identificador con formato inválido"),
            @ApiResponse(responseCode = "404", description = "Pedido no encontrado"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public List<CambioEstadoPedidoResponse> obtenerHistorial(@PathVariable Long pedidoId) {
        return service.obtenerHistorial(pedidoId).stream()
                .map(cambioEstadoMapper::toResponse)
                .toList();
    }
}
