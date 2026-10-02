package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.CambioEstadoPedidoEntityMapper;
import com.restaurante.mapper.PedidoEntityMapper;
import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.model.entity.CambioEstadoPedidoEntity;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.ItemPedidoEntity;
import com.restaurante.model.entity.PedidoEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.service.PedidoService;
import com.restaurante.service.PlatoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements PedidoService {
    private final CuentaRepository cuentaRepository;
    private final PedidoRepository pedidoRepository;
    private final PlatoService platoService;
    private final PedidoEntityMapper mapper;
    private final CambioEstadoPedidoEntityMapper cambioMapper;

    @Override
    @Transactional
    public Pedido crear(Long cuentaId) {
        CuentaEntity cuenta = buscarCuentaParaActualizar(cuentaId);
        validarCuentaAbierta(cuenta);
        PedidoEntity entity = PedidoEntity.builder()
                .cuenta(cuenta)
                .items(new ArrayList<>())
                .estado(EstadoPedido.RECIBIDO)
                .confirmado(false)
                .fechaCreacion(LocalDateTime.now())
                .fechaConfirmacion(null)
                .historialEstados(new ArrayList<>())
                .build();
        Pedido creado = mapper.toDomain(pedidoRepository.save(entity));
        log.info("Pedido creado: id={}, cuentaId={}", creado.getId(), cuentaId);
        return creado;
    }

    @Override
    @Transactional(readOnly = true)
    public Pedido obtenerPorId(Long pedidoId) {
        return mapper.toDomain(buscarPedido(pedidoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listar() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listarPorCuenta(Long cuentaId) {
        validarCuentaExiste(cuentaId);
        return pedidoRepository.findByCuentaIdOrderByIdAsc(cuentaId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Pedido agregarItem(Long pedidoId, Long platoId, int cantidad) {
        PedidoEntity pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        validarCantidad(cantidad);
        Plato plato = platoService.obtenerPorId(platoId);
        if (!plato.isDisponible()) {
            log.warn("Plato no disponible: id={}", platoId);
            throw new BusinessRuleException("El plato con id " + platoId + " no está disponible");
        }

        ItemPedidoEntity existente = pedido.getItems().stream()
                .filter(item -> Objects.equals(item.getPlatoId(), platoId))
                .findFirst()
                .orElse(null);
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + cantidad);
            return guardar(pedido);
        }

        ItemPedidoEntity item = ItemPedidoEntity.builder()
                .pedido(pedido)
                .platoId(plato.getId())
                .nombrePlato(plato.getNombre())
                .precioCongelado(plato.getPrecio())
                .cantidad(cantidad)
                .combo(plato.isCombo())
                .bebidaIncluida(plato.isCombo())
                .build();
        pedido.getItems().add(item);
        return guardar(pedido);
    }

    @Override
    @Transactional
    public Pedido actualizarCantidadItem(Long pedidoId, Long itemId, int cantidad) {
        PedidoEntity pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        validarCantidad(cantidad);
        obtenerItem(pedido, itemId).setCantidad(cantidad);
        return guardar(pedido);
    }

    @Override
    @Transactional
    public void eliminarItem(Long pedidoId, Long itemId) {
        PedidoEntity pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        pedido.getItems().remove(obtenerItem(pedido, itemId));
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public Pedido retirarBebidaCombo(Long pedidoId, Long itemId) {
        PedidoEntity pedido = obtenerPedidoEditableConCuentaAbierta(pedidoId);
        ItemPedidoEntity item = obtenerItem(pedido, itemId);
        if (!item.isCombo()) {
            throw new BusinessRuleException(
                    "El ítem con id " + itemId + " no corresponde a un combo");
        }
        item.setBebidaIncluida(false);
        return guardar(pedido);
    }

    @Override
    @Transactional
    public Pedido confirmar(Long pedidoId) {
        PedidoEntity pedido = obtenerPedidoConCuentaBloqueada(pedidoId);
        validarCuentaAbierta(pedido.getCuenta());
        if (pedido.getEstado() != EstadoPedido.RECIBIDO) {
            throw new InvalidOrderStateException(
                    "El pedido con id " + pedidoId + " no puede confirmarse en estado "
                            + pedido.getEstado());
        }
        if (pedido.isConfirmado()) {
            throw new BusinessRuleException("El pedido con id " + pedidoId + " ya está confirmado");
        }
        if (pedido.getItems().isEmpty()) {
            throw new BusinessRuleException("El pedido con id " + pedidoId + " no tiene ítems");
        }
        for (ItemPedidoEntity item : pedido.getItems()) {
            Plato plato = platoService.obtenerPorId(item.getPlatoId());
            if (!plato.isDisponible()) {
                throw new BusinessRuleException(
                        "El plato con id " + item.getPlatoId() + " no está disponible");
            }
        }
        pedido.setConfirmado(true);
        pedido.setFechaConfirmacion(LocalDateTime.now());
        return guardar(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listarParaCocina() {
        return pedidoRepository
                .findByConfirmadoTrueAndEstadoNotOrderByIdAsc(EstadoPedido.ENTREGADO).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Pedido cambiarEstado(
            Long pedidoId, EstadoPedido nuevoEstado, String usuarioResponsable) {
        PedidoEntity pedido = buscarPedidoParaActualizar(pedidoId);
        validarUsuarioResponsable(usuarioResponsable);
        EstadoPedido estadoAnterior = pedido.getEstado();
        if (!esTransicionValida(estadoAnterior, nuevoEstado)) {
            throw new InvalidOrderStateException(
                    "No se permite cambiar el pedido con id " + pedidoId + " de "
                            + estadoAnterior + " a " + nuevoEstado);
        }
        if (estadoAnterior == EstadoPedido.RECIBIDO && !pedido.isConfirmado()) {
            throw new BusinessRuleException(
                    "El pedido con id " + pedidoId
                            + " debe estar confirmado para entrar en preparación");
        }

        CambioEstadoPedidoEntity cambio = CambioEstadoPedidoEntity.builder()
                .pedido(pedido)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(nuevoEstado)
                .usuarioResponsable(usuarioResponsable)
                .fechaHora(LocalDateTime.now())
                .build();
        pedido.setEstado(nuevoEstado);
        pedido.getHistorialEstados().add(cambio);
        return guardar(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CambioEstadoPedido> obtenerHistorial(Long pedidoId) {
        return buscarPedido(pedidoId).getHistorialEstados().stream()
                .map(cambioMapper::toDomain)
                .toList();
    }

    private PedidoEntity obtenerPedidoEditableConCuentaAbierta(Long pedidoId) {
        PedidoEntity pedido = obtenerPedidoConCuentaBloqueada(pedidoId);
        validarEstadoEditable(pedido);
        validarCuentaAbierta(pedido.getCuenta());
        return pedido;
    }

    private PedidoEntity obtenerPedidoConCuentaBloqueada(Long pedidoId) {
        // Esta lectura solo identifica la Cuenta; no valida estado de la referencia.
        // Todas las operaciones con ambas filas bloquean Cuenta -> Pedido.
        PedidoEntity referencia = buscarPedido(pedidoId);
        buscarCuentaParaActualizar(referencia.getCuenta().getId());
        return buscarPedidoParaActualizar(pedidoId);
    }

    private void validarEstadoEditable(PedidoEntity pedido) {
        if (pedido.getEstado() != EstadoPedido.RECIBIDO) {
            throw new InvalidOrderStateException(
                    "El pedido con id " + pedido.getId() + " no puede modificarse en estado "
                            + pedido.getEstado());
        }
    }

    private void validarCuentaAbierta(CuentaEntity cuenta) {
        if (cuenta.getEstado() != EstadoCuenta.ABIERTA) {
            throw new BusinessRuleException(
                    "La cuenta con id " + cuenta.getId() + " no está abierta");
        }
    }

    private void validarCantidad(int cantidad) {
        if (cantidad < 1) {
            throw new BusinessRuleException("La cantidad debe ser al menos 1");
        }
    }

    private void validarUsuarioResponsable(String usuarioResponsable) {
        if (usuarioResponsable == null || usuarioResponsable.isBlank()
                || usuarioResponsable.length() > 100) {
            throw new BusinessRuleException(
                    "El usuario responsable es obligatorio y debe tener máximo 100 caracteres");
        }
    }

    private boolean esTransicionValida(EstadoPedido anterior, EstadoPedido nuevo) {
        return (anterior == EstadoPedido.RECIBIDO && nuevo == EstadoPedido.EN_PREPARACION)
                || (anterior == EstadoPedido.EN_PREPARACION && nuevo == EstadoPedido.LISTO)
                || (anterior == EstadoPedido.LISTO && nuevo == EstadoPedido.ENTREGADO);
    }

    private ItemPedidoEntity obtenerItem(PedidoEntity pedido, Long itemId) {
        return pedido.getItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el ítem con id " + itemId + " en el pedido " + pedido.getId()));
    }

    private Pedido guardar(PedidoEntity pedido) {
        return mapper.toDomain(pedidoRepository.save(pedido));
    }

    private PedidoEntity buscarPedido(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> pedidoInexistente(id));
    }

    private PedidoEntity buscarPedidoParaActualizar(Long id) {
        PedidoEntity pedido = pedidoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> pedidoInexistente(id));
        // La consulta locking no reemplaza el estado de una instancia administrada.
        pedidoRepository.refreshForUpdate(pedido);
        return pedido;
    }

    private ResourceNotFoundException pedidoInexistente(Long id) {
        log.warn("Pedido inexistente: id={}", id);
        return new ResourceNotFoundException("No existe el pedido con id: " + id);
    }

    private CuentaEntity buscarCuentaParaActualizar(Long id) {
        CuentaEntity cuenta = cuentaRepository.findByIdForUpdate(id).orElseThrow(() -> {
            log.warn("Cuenta inexistente: id={}", id);
            return new ResourceNotFoundException("No existe la cuenta con id: " + id);
        });
        cuentaRepository.refreshForUpdate(cuenta);
        return cuenta;
    }

    private void validarCuentaExiste(Long id) {
        if (!cuentaRepository.existsById(id)) {
            throw new ResourceNotFoundException("No existe la cuenta con id: " + id);
        }
    }
}
