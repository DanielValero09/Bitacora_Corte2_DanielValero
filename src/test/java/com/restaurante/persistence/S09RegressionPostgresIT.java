package com.restaurante.persistence;

import com.restaurante.RestauranteApplication;
import com.restaurante.exception.BusinessRuleException;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.service.CuentaService;
import com.restaurante.service.MesaService;
import com.restaurante.service.PagoService;
import com.restaurante.service.PedidoService;
import com.restaurante.service.PlatoService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exclusivamente manual: no coincide con los patrones de Surefire (*Test/*Tests).
 * Requiere -Dtest=S09RegressionPostgresIT -Ds09.postgres=true y DB_PASSWORD.
 * Usa servicios Spring con proxies, transacciones y PostgreSQL reales, sin mocks.
 * Conserva las filas creadas para inspección; utilizar una BD de auditoría dedicada.
 */
@EnabledIfSystemProperty(named = "s09.postgres", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class S09RegressionPostgresIT {
    private static ConfigurableApplicationContext context;

    @BeforeAll
    static void iniciar() {
        context = arrancar();
    }

    @AfterAll
    static void cerrar() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void flujoCompletoSnapshotHistorialYReaperturaSobrevivenReinicio() {
        Mesa mesa = mesas().crear(Mesa.builder().numero(numeroMesa()).build());
        Cuenta cuenta = cuentas().abrirCuenta(mesa.getId());
        Plato plato = platoPersistido();
        Pedido pedido = pedidos().crear(cuenta.getId());
        pedido = pedidos().agregarItem(pedido.getId(), plato.getId(), 1);
        Long itemId = pedido.getItems().getFirst().getId();
        assertTrue(pedido.getItems().getFirst().isCombo());
        assertTrue(pedido.getItems().getFirst().isBebidaIncluida());
        plato.setPrecio(new BigDecimal("30000.00"));
        platos().actualizar(plato.getId(), plato, List.of());
        pedidos().confirmar(pedido.getId());
        pedidos().cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero-1");
        Pago pago = pagos().registrarPago(cuenta.getId());
        assertEquals(0, new BigDecimal("20000.00").compareTo(pago.getMonto()));
        pedidos().cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero-2");
        pedidos().cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero-1");
        assertEquals(3, pedidos().obtenerHistorial(pedido.getId()).size());

        // Destruye el EntityManagerFactory y todos los services; nuevo contexto,
        // mismo PostgreSQL y ddl-auto=update. No basta con clear() o leer un mock.
        context.close();
        context = arrancar();

        assertEquals(mesa.getNumero(), mesas().obtenerPorId(mesa.getId()).getNumero());
        Cuenta cerrada = cuentas().obtenerPorId(cuenta.getId());
        Pago recuperado = pagos().obtenerPorCuenta(cuenta.getId());
        assertEquals(pago.getId(), recuperado.getId());
        assertEquals(EstadoCuenta.CERRADA, cerrada.getEstado());
        // Compara fechas reconstruidas: PostgreSQL almacena microsegundos.
        assertEquals(recuperado.getFechaHora(), cerrada.getFechaCierre());
        assertEquals(0, new BigDecimal("20000.00").compareTo(cerrada.calcularTotal()));
        Pedido reconstruido = pedidos().obtenerPorId(pedido.getId());
        assertEquals(EstadoPedido.ENTREGADO, reconstruido.getEstado());
        var item = reconstruido.getItems().getFirst();
        assertEquals(itemId, item.getId());
        assertEquals(plato.getId(), item.getPlatoId());
        assertEquals(plato.getNombre(), item.getNombrePlato());
        assertEquals(0, new BigDecimal("20000.00").compareTo(item.getPrecioCongelado()));
        assertEquals(1, item.getCantidad());
        assertTrue(item.isCombo());
        assertTrue(item.isBebidaIncluida());
        var historial = pedidos().obtenerHistorial(pedido.getId());
        assertEquals(List.of(EstadoPedido.RECIBIDO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO),
                historial.stream().map(c -> c.getEstadoAnterior()).toList());
        assertEquals(List.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO),
                historial.stream().map(c -> c.getEstadoNuevo()).toList());
        assertEquals(List.of("cocinero-1", "cocinero-2", "mesero-1"),
                historial.stream().map(c -> c.getUsuarioResponsable()).toList());
        assertTrue(historial.stream().allMatch(c -> c.getFechaHora() != null));
        assertEquals(3L, sql().queryForObject(
                "select count(*) from cambios_estado_pedido where pedido_id = ?", Long.class, pedido.getId()));
        Cuenta nueva = cuentas().abrirCuenta(mesa.getId());
        assertNotEquals(cuenta.getId(), nueva.getId());
        assertEquals(EstadoCuenta.ABIERTA, nueva.getEstado());
    }

    @Test
    void aperturaConcurrenteSoloCreaUnaCuentaAbierta() throws Exception {
        Mesa mesa = mesas().crear(Mesa.builder().numero(numeroMesa()).build());
        var resultados = competir(() -> cuentas().abrirCuenta(mesa.getId()),
                () -> cuentas().abrirCuenta(mesa.getId()));
        unaAceptadaUnaRechazada(resultados);
        assertEquals(1L, sql().queryForObject(
                "select count(*) from cuentas where mesa_id = ? and estado = 'ABIERTA'", Long.class, mesa.getId()));
    }

    @Test
    void dosIntentosConcurrentesSoloRegistranUnPago() throws Exception {
        Cuenta cuenta = cuentaNueva();
        var resultados = competir(() -> pagos().registrarPago(cuenta.getId()),
                () -> pagos().registrarPago(cuenta.getId()));
        unaAceptadaUnaRechazada(resultados);
        assertEquals(1L, sql().queryForObject(
                "select count(*) from pagos where cuenta_id = ?", Long.class, cuenta.getId()));
        assertEquals(EstadoCuenta.CERRADA, cuentas().obtenerPorId(cuenta.getId()).getEstado());
        assertEquals(pagos().obtenerPorCuenta(cuenta.getId()).getFechaHora(),
                cuentas().obtenerPorId(cuenta.getId()).getFechaCierre());
    }

    @Test
    void confirmacionesConcurrentesSoloPermitenUnaConfirmacion() throws Exception {
        Pedido pedido = pedidoConItem(cuentaNueva());
        unaAceptadaUnaRechazada(competir(() -> pedidos().confirmar(pedido.getId()),
                () -> pedidos().confirmar(pedido.getId())));
        assertTrue(pedidos().obtenerPorId(pedido.getId()).isConfirmado());
    }

    @Test
    void transicionesConcurrentesDesdeRecibidoSoloRegistranUna() throws Exception {
        Pedido pedido = pedidoConItem(cuentaNueva());
        pedidos().confirmar(pedido.getId());
        unaAceptadaUnaRechazada(competir(
                () -> pedidos().cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "uno"),
                () -> pedidos().cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "dos")));
        assertEquals(1L, sql().queryForObject(
                "select count(*) from cambios_estado_pedido where pedido_id = ?", Long.class, pedido.getId()));
    }

    @Test
    void agregarMismoPlatoConcurrentementeMantieneUnSoloItem() throws Exception {
        Pedido pedido = pedidos().crear(cuentaNueva().getId());
        Plato plato = platoPersistido();
        var resultados = competir(() -> pedidos().agregarItem(pedido.getId(), plato.getId(), 1),
                () -> pedidos().agregarItem(pedido.getId(), plato.getId(), 1));
        assertEquals(2, resultados.stream().filter(r -> r.error() == null).count());
        Pedido reconstruido = pedidos().obtenerPorId(pedido.getId());
        assertEquals(1, reconstruido.getItems().size());
        assertEquals(2, reconstruido.getItems().getFirst().getCantidad());
    }

    @Test
    void pagoConcurrenteConAgregarItemConservaElTotalDeLaCuentaCerrada() throws Exception {
        Cuenta cuenta = cuentaNueva();
        Pedido pedido = pedidoConItem(cuenta);
        Long platoId = pedido.getItems().getFirst().getPlatoId();
        competir(() -> pagos().registrarPago(cuenta.getId()),
                () -> pedidos().agregarItem(pedido.getId(), platoId, 1));
        assertEquals(EstadoCuenta.CERRADA, cuentas().obtenerPorId(cuenta.getId()).getEstado());
        assertEquals(0, pagos().obtenerPorCuenta(cuenta.getId()).getMonto()
                .compareTo(cuentas().obtenerPorId(cuenta.getId()).calcularTotal()));
        assertThrows(BusinessRuleException.class,
                () -> pedidos().agregarItem(pedido.getId(), platoId, 1));
    }

    @Test
    void contextoPrecargadoAntesDelPagoNoDebePermitirEditarCuentaCerrada() throws Exception {
        // Regresión pendiente señalada en la auditoría: lock no equivale a refresh.
        // Precarga legítima en una transacción llamadora, paga en otra conexión,
        // luego modifica. El resultado esperado es rechazo y total inalterado.
        Cuenta cuenta = cuentaNueva();
        Pedido pedido = pedidoConItem(cuenta);
        Long platoId = pedido.getItems().getFirst().getPlatoId();
        CountDownLatch bloqueada = new CountDownLatch(1);
        CountDownLatch precargada = new CountDownLatch(1);
        CountDownLatch pagada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pago = executor.submit(() -> {
                try {
                    return transaccion().execute(status -> {
                        context.getBean(CuentaRepository.class).findByIdForUpdate(cuenta.getId()).orElseThrow();
                        bloqueada.countDown();
                        esperar(precargada);
                        return pagos().registrarPago(cuenta.getId());
                    });
                } finally {
                    pagada.countDown();
                }
            });
            // Verifica el rechazo despues de que TransactionTemplate haga rollback.
            // Capturarlo dentro del callback provocaria un commit de una transaccion rollback-only.
            var edicion = executor.submit(() -> assertThrows(BusinessRuleException.class,
                    () -> transaccion().execute(status -> {
                        esperar(bloqueada);
                        cuentas().obtenerPorId(cuenta.getId());
                        pedidos().obtenerPorId(pedido.getId());
                        precargada.countDown();
                        esperar(pagada);
                        return pedidos().agregarItem(pedido.getId(), platoId, 1);
                    })));
            pago.get(30, TimeUnit.SECONDS);
            BusinessRuleException rechazo = edicion.get(30, TimeUnit.SECONDS);
            assertEquals("La cuenta con id " + cuenta.getId() + " no está abierta", rechazo.getMessage());
        }
        assertEquals(0, pagos().obtenerPorCuenta(cuenta.getId()).getMonto()
                .compareTo(cuentas().obtenerPorId(cuenta.getId()).calcularTotal()));
    }

    @Test
    void restriccionesRealesOrphanRemovalYCierreAtomico() {
        Cuenta cuenta = cuentaNueva();
        assertEquals("ALWAYS", sql().queryForObject("select is_generated from information_schema.columns "
                + "where table_schema = current_schema() and table_name = 'cuentas' "
                + "and column_name = 'cuenta_abierta'", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> sql().update(
                "insert into cuentas (mesa_id, estado, fecha_apertura) values (?, 'ABIERTA', current_timestamp)",
                cuenta.getMesaId()));
        Pedido pedido = pedidoConItem(cuenta);
        Long itemId = pedido.getItems().getFirst().getId();
        pedidos().eliminarItem(pedido.getId(), itemId);
        assertEquals(0L, sql().queryForObject("select count(*) from items_pedido where id = ?", Long.class, itemId));
        assertNotNull(pedidos().obtenerPorId(pedido.getId()));
        assertThrows(IllegalStateException.class, () -> transaccion().execute(status -> {
            pagos().registrarPago(cuenta.getId());
            throw new IllegalStateException("forzar rollback de pago y cierre");
        }));
        assertEquals(EstadoCuenta.ABIERTA, cuentas().obtenerPorId(cuenta.getId()).getEstado());
        assertEquals(0L, sql().queryForObject("select count(*) from pagos where cuenta_id = ?", Long.class, cuenta.getId()));
        pagos().registrarPago(cuenta.getId());
        assertThrows(DataIntegrityViolationException.class, () -> sql().update(
                "insert into pagos (cuenta_id, monto, fecha_hora) values (?, 0, current_timestamp)", cuenta.getId()));
        Cuenta otra = cuentas().abrirCuenta(cuenta.getMesaId());
        pagos().registrarPago(otra.getId());
        assertEquals(EstadoCuenta.ABIERTA, cuentas().abrirCuenta(cuenta.getMesaId()).getEstado());
        assertEquals(2L, sql().queryForObject("select count(*) from cuentas "
                + "where mesa_id = ? and estado = 'CERRADA' and cuenta_abierta is null", Long.class, cuenta.getMesaId()));
    }

    private static ConfigurableApplicationContext arrancar() {
        return new SpringApplicationBuilder(RestauranteApplication.class).web(WebApplicationType.NONE)
                .run("--spring.jpa.hibernate.ddl-auto=update", "--spring.jpa.open-in-view=false",
                        "--spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
                        "--spring.datasource.hikari.connection-timeout=10000",
                        "--spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'");
    }

    private List<Resultado> competir(Callable<?> primera, Callable<?> segunda) throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var uno = executor.submit(() -> ejecutarTras(inicio, primera));
            var dos = executor.submit(() -> ejecutarTras(inicio, segunda));
            inicio.countDown();
            return List.of(uno.get(30, TimeUnit.SECONDS), dos.get(30, TimeUnit.SECONDS));
        }
    }

    private Resultado ejecutarTras(CountDownLatch inicio, Callable<?> tarea) throws Exception {
        esperar(inicio);
        try {
            return new Resultado(tarea.call(), null);
        } catch (BusinessRuleException exception) {
            return new Resultado(null, exception);
        }
    }

    private void unaAceptadaUnaRechazada(List<Resultado> resultados) {
        assertEquals(1, resultados.stream().filter(r -> r.error() == null).count());
        assertEquals(1, resultados.stream().filter(r -> r.error() instanceof BusinessRuleException).count());
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La otra transacción no alcanzó la barrera");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Cuenta cuentaNueva() {
        return cuentas().abrirCuenta(mesas().crear(Mesa.builder().numero(numeroMesa()).build()).getId());
    }

    private Pedido pedidoConItem(Cuenta cuenta) {
        return pedidos().agregarItem(pedidos().crear(cuenta.getId()).getId(), platoPersistido().getId(), 1);
    }

    private Plato platoPersistido() {
        return platos().crear(Plato.builder().nombre("Auditoria-" + UUID.randomUUID())
                .precio(new BigDecimal("20000.00")).combo(true).build(), List.of());
    }

    private int numeroMesa() { return ThreadLocalRandom.current().nextInt(100_000, 1_000_000_000); }
    private MesaService mesas() { return context.getBean(MesaService.class); }
    private CuentaService cuentas() { return context.getBean(CuentaService.class); }
    private PedidoService pedidos() { return context.getBean(PedidoService.class); }
    private PagoService pagos() { return context.getBean(PagoService.class); }
    private PlatoService platos() { return context.getBean(PlatoService.class); }
    private JdbcTemplate sql() { return context.getBean(JdbcTemplate.class); }
    private TransactionTemplate transaccion() {
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    private record Resultado(Object valor, BusinessRuleException error) { }
}
