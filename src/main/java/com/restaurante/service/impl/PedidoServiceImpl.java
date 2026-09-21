package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.service.CuentaService;
import com.restaurante.service.PedidoService;
import com.restaurante.service.PlatoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {
    private final CuentaService cuentaService;
    private final PlatoService platoService;
    private final ConcurrentHashMap<Long, Pedido> pedidos = new ConcurrentHashMap<>();
    private final AtomicLong secuenciaPedidos = new AtomicLong();
    private final AtomicLong secuenciaItems = new AtomicLong();

    @Override
    public synchronized Pedido crear(Long cuentaId) {
        Cuenta cuenta = cuentaService.obtenerPorId(cuentaId);
        validarCuentaAbierta(cuenta);

        Pedido pedido = Pedido.builder()
                .id(secuenciaPedidos.incrementAndGet())
                .cuentaId(cuentaId)
                .items(new ArrayList<>())
                .estado(EstadoPedido.RECIBIDO)
                .confirmado(false)
                .fechaCreacion(LocalDateTime.now())
                .fechaConfirmacion(null)
                .historialEstados(new ArrayList<>())
                .build();
        pedidos.put(pedido.getId(), pedido);
        cuenta.getPedidos().add(pedido);
        log.info("Pedido creado: id={}, cuentaId={}", pedido.getId(), cuentaId);
        return pedido;
    }

    @Override
    public Pedido obtenerPorId(Long pedidoId) {
        Pedido pedido = pedidos.get(pedidoId);
        if (pedido == null) {
            log.warn("Pedido inexistente: id={}", pedidoId);
            throw new ResourceNotFoundException("No existe el pedido con id: " + pedidoId);
        }
        return pedido;
    }

    @Override
    public List<Pedido> listar() {
        return pedidos.values().stream()
                .sorted(Comparator.comparing(Pedido::getId))
                .toList();
    }

    @Override
    public List<Pedido> listarPorCuenta(Long cuentaId) {
        cuentaService.obtenerPorId(cuentaId);
        return pedidos.values().stream()
                .filter(pedido -> Objects.equals(pedido.getCuentaId(), cuentaId))
                .sorted(Comparator.comparing(Pedido::getId))
                .toList();
    }

    @Override
    public synchronized Pedido agregarItem(Long pedidoId, Long platoId, int cantidad) {
        Pedido pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        validarCantidad(cantidad);
        Plato plato = platoService.obtenerPorId(platoId);
        if (!plato.isDisponible()) {
            log.warn("Plato no disponible: id={}", platoId);
            throw new BusinessRuleException("El plato con id " + platoId + " no está disponible");
        }

        ItemPedido existente = pedido.getItems().stream()
                .filter(item -> Objects.equals(item.getPlatoId(), platoId))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + cantidad);
            log.info("Ítem agregado a cantidad existente: pedidoId={}, itemId={}, cantidad={}",
                    pedidoId, existente.getId(), existente.getCantidad());
            return pedido;
        }

        ItemPedido item = ItemPedido.builder()
                .id(secuenciaItems.incrementAndGet())
                .platoId(plato.getId())
                .nombrePlato(plato.getNombre())
                .precioCongelado(plato.getPrecio())
                .cantidad(cantidad)
                .combo(plato.isCombo())
                .bebidaIncluida(plato.isCombo())
                .build();
        pedido.getItems().add(item);
        log.info("Ítem agregado: pedidoId={}, itemId={}, platoId={}", pedidoId, item.getId(), platoId);
        return pedido;
    }

    @Override
    public synchronized Pedido actualizarCantidadItem(Long pedidoId, Long itemId, int cantidad) {
        Pedido pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        validarCantidad(cantidad);
        ItemPedido item = obtenerItem(pedido, itemId);
        item.setCantidad(cantidad);
        log.info("Cantidad actualizada: pedidoId={}, itemId={}, cantidad={}", pedidoId, itemId, cantidad);
        return pedido;
    }

    @Override
    public synchronized void eliminarItem(Long pedidoId, Long itemId) {
        Pedido pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        ItemPedido item = obtenerItem(pedido, itemId);
        pedido.getItems().remove(item);
        log.info("Ítem eliminado: pedidoId={}, itemId={}", pedidoId, itemId);
    }

    @Override
    public synchronized Pedido retirarBebidaCombo(Long pedidoId, Long itemId) {
        Pedido pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        ItemPedido item = obtenerItem(pedido, itemId);
        if (!item.isCombo()) {
            log.warn("Intento de retirar bebida de producto no combo: pedidoId={}, itemId={}",
                    pedidoId, itemId);
            throw new BusinessRuleException("El ítem con id " + itemId + " no corresponde a un combo");
        }
        item.setBebidaIncluida(false);
        log.info("Bebida retirada: pedidoId={}, itemId={}", pedidoId, itemId);
        return pedido;
    }

    private Pedido obtenerPedidoEditableConCuentaAbierta(Long pedidoId) {
        Pedido pedido = obtenerPorId(pedidoId);
        validarEstadoEditable(pedido);
        Cuenta cuenta = cuentaService.obtenerPorId(pedido.getCuentaId());
        validarCuentaAbierta(cuenta);
        return pedido;
    }

    private void validarEstadoEditable(Pedido pedido) {
        if (pedido.getEstado() != EstadoPedido.RECIBIDO) {
            log.warn("Pedido en estado no editable: id={}, estado={}", pedido.getId(), pedido.getEstado());
            throw new InvalidOrderStateException(
                    "El pedido con id " + pedido.getId() + " no puede modificarse en estado "
                            + pedido.getEstado());
        }
    }

    private void validarCuentaAbierta(Cuenta cuenta) {
        if (cuenta.getEstado() != EstadoCuenta.ABIERTA) {
            log.warn("Cuenta cerrada: id={}", cuenta.getId());
            throw new BusinessRuleException("La cuenta con id " + cuenta.getId() + " no está abierta");
        }
    }

    private void validarCantidad(int cantidad) {
        if (cantidad < 1) {
            throw new BusinessRuleException("La cantidad debe ser al menos 1");
        }
    }

    private ItemPedido obtenerItem(Pedido pedido, Long itemId) {
        return pedido.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Ítem inexistente en pedido: pedidoId={}, itemId={}", pedido.getId(), itemId);
                    return new ResourceNotFoundException(
                            "No existe el ítem con id " + itemId + " en el pedido " + pedido.getId());
                });
    }
}
