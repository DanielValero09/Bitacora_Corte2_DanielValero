package com.restaurante.support;

import com.restaurante.mapper.CambioEstadoPedidoEntityMapper;
import com.restaurante.mapper.CuentaEntityMapper;
import com.restaurante.mapper.ItemPedidoEntityMapper;
import com.restaurante.mapper.MesaEntityMapper;
import com.restaurante.mapper.PagoEntityMapper;
import com.restaurante.mapper.PedidoEntityMapper;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.model.entity.CambioEstadoPedidoEntity;
import com.restaurante.model.entity.CuentaEntity;
import com.restaurante.model.entity.ItemPedidoEntity;
import com.restaurante.model.entity.MesaEntity;
import com.restaurante.model.entity.PagoEntity;
import com.restaurante.model.entity.PedidoEntity;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PagoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.service.PlatoService;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PagoServiceImpl;
import com.restaurante.service.impl.PedidoServiceImpl;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Repositories en memoria exclusivos de prueba para integrar los services JPA sin otra BD. */
public final class RelationalTestFixture {
    private final Map<Long, MesaEntity> mesasGuardadas = new LinkedHashMap<>();
    private final Map<Long, CuentaEntity> cuentasGuardadas = new LinkedHashMap<>();
    private final Map<Long, PedidoEntity> pedidosGuardados = new LinkedHashMap<>();
    private final Map<Long, PagoEntity> pagosGuardados = new LinkedHashMap<>();
    private final AtomicLong mesasIds = new AtomicLong();
    private final AtomicLong cuentasIds = new AtomicLong();
    private final AtomicLong pedidosIds = new AtomicLong();
    private final AtomicLong itemsIds = new AtomicLong();
    private final AtomicLong cambiosIds = new AtomicLong();
    private final AtomicLong pagosIds = new AtomicLong();

    private final MesaServiceImpl mesas;
    private final CuentaServiceImpl cuentas;
    private final PedidoServiceImpl pedidos;
    private final PagoServiceImpl pagos;

    public RelationalTestFixture(PlatoService platoService) {
        this(platoService, mock(ApplicationEventPublisher.class));
    }

    public RelationalTestFixture(PlatoService platoService, ApplicationEventPublisher publisher) {
        MesaRepository mesaRepository = mock(MesaRepository.class, withSettings().lenient());
        CuentaRepository cuentaRepository = mock(CuentaRepository.class, withSettings().lenient());
        PedidoRepository pedidoRepository = mock(PedidoRepository.class, withSettings().lenient());
        PagoRepository pagoRepository = mock(PagoRepository.class, withSettings().lenient());
        configurarMesas(mesaRepository);
        configurarCuentas(cuentaRepository);
        configurarPedidos(pedidoRepository);
        configurarPagos(pagoRepository);

        MesaEntityMapper mesaMapper = Mappers.getMapper(MesaEntityMapper.class);
        ItemPedidoEntityMapper itemMapper = Mappers.getMapper(ItemPedidoEntityMapper.class);
        CambioEstadoPedidoEntityMapper cambioMapper =
                Mappers.getMapper(CambioEstadoPedidoEntityMapper.class);
        PedidoEntityMapper pedidoMapper = Mappers.getMapper(PedidoEntityMapper.class);
        ReflectionTestUtils.setField(pedidoMapper, "itemPedidoEntityMapper", itemMapper);
        ReflectionTestUtils.setField(pedidoMapper, "cambioEstadoPedidoEntityMapper", cambioMapper);
        CuentaEntityMapper cuentaMapper = Mappers.getMapper(CuentaEntityMapper.class);
        ReflectionTestUtils.setField(cuentaMapper, "pedidoEntityMapper", pedidoMapper);
        PagoEntityMapper pagoMapper = Mappers.getMapper(PagoEntityMapper.class);

        mesas = new MesaServiceImpl(mesaRepository, mesaMapper);
        cuentas = new CuentaServiceImpl(mesaRepository, cuentaRepository, cuentaMapper);
        pedidos = new PedidoServiceImpl(
                cuentaRepository, pedidoRepository, platoService, pedidoMapper, cambioMapper, publisher);
        pagos = new PagoServiceImpl(cuentaRepository, pagoRepository, cuentaMapper, pagoMapper);
    }

    public MesaServiceImpl mesas() {
        return mesas;
    }

    public CuentaServiceImpl cuentas() {
        return cuentas;
    }

    public PedidoServiceImpl pedidos() {
        return pedidos;
    }

    public PagoServiceImpl pagos() {
        return pagos;
    }

    private void configurarMesas(MesaRepository repository) {
        when(repository.existsByNumero(any())).thenAnswer(invocation -> {
            Integer numero = invocation.getArgument(0);
            return mesasGuardadas.values().stream().anyMatch(m -> m.getNumero().equals(numero));
        });
        when(repository.existsById(anyLong())).thenAnswer(invocation ->
                mesasGuardadas.containsKey(invocation.<Long>getArgument(0)));
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(mesasGuardadas.get(invocation.<Long>getArgument(0))));
        when(repository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(mesasGuardadas.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> ordenarMesas());
        when(repository.saveAndFlush(any(MesaEntity.class))).thenAnswer(invocation -> {
            MesaEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(mesasIds.incrementAndGet());
            }
            mesasGuardadas.put(entity.getId(), entity);
            return entity;
        });
    }

    private void configurarCuentas(CuentaRepository repository) {
        when(repository.existsById(anyLong())).thenAnswer(invocation ->
                cuentasGuardadas.containsKey(invocation.<Long>getArgument(0)));
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(cuentasGuardadas.get(invocation.<Long>getArgument(0))));
        when(repository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(cuentasGuardadas.get(invocation.<Long>getArgument(0))));
        when(repository.existsByMesaIdAndEstado(anyLong(), any(EstadoCuenta.class)))
                .thenAnswer(invocation -> buscarCuenta(
                        invocation.getArgument(0), invocation.getArgument(1)).isPresent());
        when(repository.findByMesaIdAndEstado(anyLong(), any(EstadoCuenta.class)))
                .thenAnswer(invocation -> buscarCuenta(
                        invocation.getArgument(0), invocation.getArgument(1)));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> cuentasGuardadas.values()
                .stream().sorted(Comparator.comparing(CuentaEntity::getId)).toList());
        when(repository.save(any(CuentaEntity.class))).thenAnswer(invocation -> {
            CuentaEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(cuentasIds.incrementAndGet());
            }
            cuentasGuardadas.put(entity.getId(), entity);
            return entity;
        });
    }

    private void configurarPedidos(PedidoRepository repository) {
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(pedidosGuardados.get(invocation.<Long>getArgument(0))));
        when(repository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(pedidosGuardados.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> pedidosGuardados.values()
                .stream().sorted(Comparator.comparing(PedidoEntity::getId)).toList());
        when(repository.findByCuentaIdOrderByIdAsc(anyLong())).thenAnswer(invocation -> {
            Long cuentaId = invocation.getArgument(0);
            return pedidosGuardados.values().stream()
                    .filter(p -> p.getCuenta().getId().equals(cuentaId))
                    .sorted(Comparator.comparing(PedidoEntity::getId)).toList();
        });
        when(repository.findByConfirmadoTrueAndEstadoNotOrderByIdAsc(any(EstadoPedido.class)))
                .thenAnswer(invocation -> {
                    EstadoPedido excluido = invocation.getArgument(0);
                    return pedidosGuardados.values().stream()
                            .filter(PedidoEntity::isConfirmado)
                            .filter(p -> p.getEstado() != excluido)
                            .sorted(Comparator.comparing(PedidoEntity::getId)).toList();
                });
        when(repository.save(any(PedidoEntity.class))).thenAnswer(invocation -> {
            PedidoEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(pedidosIds.incrementAndGet());
            }
            asignarIds(entity);
            pedidosGuardados.put(entity.getId(), entity);
            if (!entity.getCuenta().getPedidos().contains(entity)) {
                entity.getCuenta().getPedidos().add(entity);
            }
            return entity;
        });
    }

    private void configurarPagos(PagoRepository repository) {
        when(repository.existsByCuentaId(anyLong())).thenAnswer(invocation ->
                buscarPago(invocation.getArgument(0)).isPresent());
        when(repository.findByCuentaId(anyLong())).thenAnswer(invocation ->
                buscarPago(invocation.getArgument(0)));
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(pagosGuardados.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> pagosGuardados.values()
                .stream().sorted(Comparator.comparing(PagoEntity::getId)).toList());
        when(repository.saveAndFlush(any(PagoEntity.class))).thenAnswer(invocation -> {
            PagoEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(pagosIds.incrementAndGet());
            }
            pagosGuardados.put(entity.getId(), entity);
            return entity;
        });
    }

    private List<MesaEntity> ordenarMesas() {
        return mesasGuardadas.values().stream()
                .sorted(Comparator.comparing(MesaEntity::getId)).toList();
    }

    private Optional<CuentaEntity> buscarCuenta(Long mesaId, EstadoCuenta estado) {
        return cuentasGuardadas.values().stream()
                .filter(c -> c.getMesa().getId().equals(mesaId) && c.getEstado() == estado)
                .findFirst();
    }

    private Optional<PagoEntity> buscarPago(Long cuentaId) {
        return pagosGuardados.values().stream()
                .filter(p -> p.getCuenta().getId().equals(cuentaId)).findFirst();
    }

    private void asignarIds(PedidoEntity pedido) {
        for (ItemPedidoEntity item : pedido.getItems()) {
            if (item.getId() == null) {
                item.setId(itemsIds.incrementAndGet());
            }
        }
        for (CambioEstadoPedidoEntity cambio : pedido.getHistorialEstados()) {
            if (cambio.getId() == null) {
                cambio.setId(cambiosIds.incrementAndGet());
            }
        }
    }
}
