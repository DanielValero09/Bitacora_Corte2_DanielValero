package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.service.CuentaService;
import com.restaurante.service.PlatoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceImplTest {
    @Mock
    private CuentaService cuentaService;
    @Mock
    private PlatoService platoService;

    private PedidoServiceImpl service;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        service = new PedidoServiceImpl(cuentaService, platoService);
        cuenta = Cuenta.builder()
                .id(10L)
                .estado(EstadoCuenta.ABIERTA)
                .pedidos(new ArrayList<>())
                .build();
    }

    @Test
    void crearPedidoEnCuentaAbiertaInicializaDatosYConservaMismaInstancia() {
        cuentaExistente();

        Pedido pedido = service.crear(cuenta.getId());

        assertEquals(1L, pedido.getId());
        assertEquals(cuenta.getId(), pedido.getCuentaId());
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertFalse(pedido.isConfirmado());
        assertNotNull(pedido.getFechaCreacion());
        assertNull(pedido.getFechaConfirmacion());
        assertTrue(pedido.getItems().isEmpty());
        assertTrue(pedido.getHistorialEstados().isEmpty());
        assertSame(pedido, cuenta.getPedidos().getFirst());
    }

    @Test
    void cuentaCerradaImpideCrearPedido() {
        cuenta.setEstado(EstadoCuenta.CERRADA);
        cuentaExistente();

        assertThrows(BusinessRuleException.class, () -> service.crear(cuenta.getId()));
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void obtenerPedidoExistenteDevuelveMismaInstancia() {
        Pedido pedido = crearPedido();

        assertSame(pedido, service.obtenerPorId(pedido.getId()));
    }

    @Test
    void obtenerPedidoInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarDevuelvePedidosOrdenados() {
        cuentaExistente();
        Pedido primero = service.crear(cuenta.getId());
        Pedido segundo = service.crear(cuenta.getId());

        assertEquals(List.of(primero, segundo), service.listar());
    }

    @Test
    void listarPorCuentaExistenteFiltraPedidos() {
        cuentaExistente();
        Pedido pedido = service.crear(cuenta.getId());
        Cuenta otra = Cuenta.builder().id(20L).estado(EstadoCuenta.ABIERTA)
                .pedidos(new ArrayList<>()).build();
        when(cuentaService.obtenerPorId(otra.getId())).thenReturn(otra);
        service.crear(otra.getId());

        assertEquals(List.of(pedido), service.listarPorCuenta(cuenta.getId()));
    }

    @Test
    void listarPorCuentaInexistentePropagaExcepcion() {
        when(cuentaService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Cuenta inexistente"));

        assertThrows(ResourceNotFoundException.class, () -> service.listarPorCuenta(99L));
    }

    @Test
    void agregarItemCreaSnapshotCompletoDeNoCombo() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20000.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);

        service.agregarItem(pedido.getId(), plato.getId(), 2);

        ItemPedido item = pedido.getItems().getFirst();
        assertEquals(1L, item.getId());
        assertEquals(plato.getId(), item.getPlatoId());
        assertEquals("Hamburguesa", item.getNombrePlato());
        assertEquals(new BigDecimal("20000.00"), item.getPrecioCongelado());
        assertEquals(2, item.getCantidad());
        assertFalse(item.isCombo());
        assertFalse(item.isBebidaIncluida());
    }

    @Test
    void comboIniciaConBebidaIncluida() {
        Pedido pedido = crearPedido();
        Plato combo = platoDisponible(30L, "Combo", "25000", true);
        when(platoService.obtenerPorId(combo.getId())).thenReturn(combo);

        service.agregarItem(pedido.getId(), combo.getId(), 1);

        assertTrue(pedido.getItems().getFirst().isCombo());
        assertTrue(pedido.getItems().getFirst().isBebidaIncluida());
    }

    @Test
    void platoInexistenteImpideAgregar() {
        Pedido pedido = crearPedido();
        when(platoService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("Plato inexistente"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.agregarItem(pedido.getId(), 99L, 1));
        assertTrue(pedido.getItems().isEmpty());
    }

    @Test
    void platoInactivoImpideAgregar() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20000", false);
        plato.setActivo(false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), plato.getId(), 1));
    }

    @Test
    void platoConIngredienteAgotadoImpideAgregar() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20000", false);
        plato.setIngredientes(List.of(Ingrediente.builder().disponible(false).build()));
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), plato.getId(), 1));
    }

    @Test
    void cuentaCerradaImpideModificarPedido() {
        Pedido pedido = crearPedido();
        cuenta.setEstado(EstadoCuenta.CERRADA);

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), 30L, 1));
    }

    @Test
    void estadoDistintoDeRecibidoImpideModificarPedido() {
        Pedido pedido = crearPedido();
        pedido.setEstado(EstadoPedido.EN_PREPARACION);

        assertThrows(InvalidOrderStateException.class,
                () -> service.agregarItem(pedido.getId(), 30L, 1));
    }

    @Test
    void productoRepetidoIncrementaCantidadYConservaSnapshotOriginal() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Nombre inicial", "20000.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        service.agregarItem(pedido.getId(), plato.getId(), 2);
        plato.setNombre("Nombre nuevo");
        plato.setPrecio(new BigDecimal("25000.00"));

        service.agregarItem(pedido.getId(), plato.getId(), 1);

        assertEquals(1, pedido.getItems().size());
        ItemPedido item = pedido.getItems().getFirst();
        assertEquals(3, item.getCantidad());
        assertEquals("Nombre inicial", item.getNombrePlato());
        assertEquals(new BigDecimal("20000.00"), item.getPrecioCongelado());
    }

    @Test
    void precioCongeladoYTotalNoCambianCuandoCambiaPrecioDelPlato() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        service.agregarItem(pedido.getId(), plato.getId(), 2);

        plato.setPrecio(new BigDecimal("25.00"));

        assertEquals(new BigDecimal("20.00"), pedido.getItems().getFirst().getPrecioCongelado());
        assertEquals(new BigDecimal("40.00"), pedido.calcularTotal());
    }

    @Test
    void relacionCuentaPedidoReflejaTotalTrasAgregarItem() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        service.agregarItem(pedido.getId(), plato.getId(), 3);

        assertSame(pedido, cuenta.getPedidos().getFirst());
        assertEquals(new BigDecimal("60.00"), cuenta.calcularTotal());
    }

    @Test
    void actualizarCantidadSoloCambiaCantidad() {
        Pedido pedido = pedidoConItem(false);
        ItemPedido item = pedido.getItems().getFirst();
        BigDecimal precio = item.getPrecioCongelado();

        service.actualizarCantidadItem(pedido.getId(), item.getId(), 4);

        assertEquals(4, item.getCantidad());
        assertEquals(precio, item.getPrecioCongelado());
    }

    @Test
    void cantidadInvalidaImpideAgregarYActualizar() {
        Pedido pedido = crearPedido();
        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), 30L, 0));
        Pedido conItem = pedidoConItem(false);
        Long itemId = conItem.getItems().getFirst().getId();
        assertThrows(BusinessRuleException.class,
                () -> service.actualizarCantidadItem(conItem.getId(), itemId, 0));
    }

    @Test
    void itemInexistenteImpideActualizarEliminarYRetirarBebida() {
        Pedido pedido = crearPedido();

        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), 99L, 1));
        assertThrows(ResourceNotFoundException.class,
                () -> service.eliminarItem(pedido.getId(), 99L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.retirarBebidaCombo(pedido.getId(), 99L));
    }

    @Test
    void eliminarItemLoRetiraSinEliminarPedido() {
        Pedido pedido = pedidoConItem(false);
        Long itemId = pedido.getItems().getFirst().getId();

        service.eliminarItem(pedido.getId(), itemId);

        assertTrue(pedido.getItems().isEmpty());
        assertSame(pedido, service.obtenerPorId(pedido.getId()));
    }

    @Test
    void retirarBebidaDeComboEsIdempotenteYConservaPrecioCantidadYSubtotal() {
        Pedido pedido = pedidoConItem(true);
        ItemPedido item = pedido.getItems().getFirst();
        BigDecimal precio = item.getPrecioCongelado();
        BigDecimal subtotal = item.calcularSubtotal();
        int cantidad = item.getCantidad();

        service.retirarBebidaCombo(pedido.getId(), item.getId());
        service.retirarBebidaCombo(pedido.getId(), item.getId());

        assertFalse(item.isBebidaIncluida());
        assertEquals(precio, item.getPrecioCongelado());
        assertEquals(cantidad, item.getCantidad());
        assertEquals(subtotal, item.calcularSubtotal());
    }

    @Test
    void retirarBebidaDeNoComboLanzaExcepcion() {
        Pedido pedido = pedidoConItem(false);

        assertThrows(BusinessRuleException.class,
                () -> service.retirarBebidaCombo(
                        pedido.getId(), pedido.getItems().getFirst().getId()));
    }

    @Test
    void confirmarPedidoValidoMarcaConfirmacionConFechaSinCambiarEstadoNiHistorial() {
        Pedido pedido = pedidoConItem(false);

        Pedido resultado = service.confirmar(pedido.getId());

        assertSame(pedido, resultado);
        assertTrue(pedido.isConfirmado());
        assertNotNull(pedido.getFechaConfirmacion());
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    @Test
    void pedidoVacioNoSeConfirma() {
        Pedido pedido = crearPedido();

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));

        assertFalse(pedido.isConfirmado());
        assertNull(pedido.getFechaConfirmacion());
    }

    @Test
    void segundaConfirmacionLanzaExcepcionSinAlterarFechaOriginal() {
        Pedido pedido = pedidoConItem(false);
        service.confirmar(pedido.getId());
        var fechaOriginal = pedido.getFechaConfirmacion();

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));

        assertEquals(fechaOriginal, pedido.getFechaConfirmacion());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    @Test
    void cuentaCerradaImpideConfirmar() {
        Pedido pedido = pedidoConItem(false);
        cuenta.setEstado(EstadoCuenta.CERRADA);

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));
        assertFalse(pedido.isConfirmado());
    }

    @Test
    void estadoDistintoDeRecibidoImpideConfirmar() {
        Pedido pedido = pedidoConItem(false);
        pedido.setEstado(EstadoPedido.EN_PREPARACION);

        assertThrows(InvalidOrderStateException.class, () -> service.confirmar(pedido.getId()));
        assertFalse(pedido.isConfirmado());
    }

    @Test
    void productoAgotadoDespuesDeAgregarseImpideConfirmar() {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        service.agregarItem(pedido.getId(), plato.getId(), 1);
        plato.setActivo(false);

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));
        assertFalse(pedido.isConfirmado());
    }

    @Test
    void platoEliminadoAntesDeConfirmarPropagaRecursoInexistente() {
        Pedido pedido = pedidoConItem(false);
        Long platoId = pedido.getItems().getFirst().getPlatoId();
        when(platoService.obtenerPorId(platoId))
                .thenThrow(new ResourceNotFoundException("Plato inexistente"));

        assertThrows(ResourceNotFoundException.class, () -> service.confirmar(pedido.getId()));
        assertFalse(pedido.isConfirmado());
    }

    @Test
    void cambioDePrecioNoModificaPrecioCongeladoAlConfirmar() {
        Pedido pedido = pedidoConItem(false);
        ItemPedido item = pedido.getItems().getFirst();
        BigDecimal precioCongelado = item.getPrecioCongelado();
        Plato plato = platoService.obtenerPorId(item.getPlatoId());
        plato.setPrecio(new BigDecimal("999.00"));

        service.confirmar(pedido.getId());

        assertEquals(precioCongelado, item.getPrecioCongelado());
        assertEquals(precioCongelado.multiply(BigDecimal.valueOf(item.getCantidad())),
                item.calcularSubtotal());
    }

    @Test
    void todosLosProductosDisponiblesPermitenConfirmar() {
        Pedido pedido = crearPedido();
        Plato primero = platoDisponible(30L, "Plato", "20.00", false);
        Plato segundo = platoDisponible(31L, "Combo", "30.00", true);
        when(platoService.obtenerPorId(primero.getId())).thenReturn(primero);
        when(platoService.obtenerPorId(segundo.getId())).thenReturn(segundo);
        service.agregarItem(pedido.getId(), primero.getId(), 1);
        service.agregarItem(pedido.getId(), segundo.getId(), 1);

        service.confirmar(pedido.getId());

        assertTrue(pedido.isConfirmado());
        assertEquals(2, pedido.getItems().size());
    }

    @Test
    void tableroVacioCuandoNoHayPedidosElegibles() {
        crearPedido();

        assertTrue(service.listarParaCocina().isEmpty());
    }

    @Test
    void tableroIncluyeConfirmadosActivosYExcluyeNoConfirmadosYEntregados() {
        Pedido noConfirmado = crearPedido();
        Pedido recibido = crearPedido();
        recibido.setConfirmado(true);
        Pedido enPreparacion = crearPedido();
        enPreparacion.setConfirmado(true);
        enPreparacion.setEstado(EstadoPedido.EN_PREPARACION);
        Pedido listo = crearPedido();
        listo.setConfirmado(true);
        listo.setEstado(EstadoPedido.LISTO);
        Pedido entregado = crearPedido();
        entregado.setConfirmado(true);
        entregado.setEstado(EstadoPedido.ENTREGADO);

        assertEquals(List.of(recibido, enPreparacion, listo), service.listarParaCocina());
        assertFalse(service.listarParaCocina().contains(noConfirmado));
        assertFalse(service.listarParaCocina().contains(entregado));
    }

    @Test
    void recorridoCompletoRegistraTresCambiosConTodosLosDatos() {
        Pedido pedido = crearPedido();
        pedido.setConfirmado(true);

        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero-1");
        service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero-2");
        service.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero-1");

        assertEquals(EstadoPedido.ENTREGADO, pedido.getEstado());
        assertEquals(3, pedido.getHistorialEstados().size());
        CambioEstadoPedido primero = pedido.getHistorialEstados().getFirst();
        assertEquals(EstadoPedido.RECIBIDO, primero.getEstadoAnterior());
        assertEquals(EstadoPedido.EN_PREPARACION, primero.getEstadoNuevo());
        assertEquals("cocinero-1", primero.getUsuarioResponsable());
        assertNotNull(primero.getFechaHora());
        assertEquals(pedido.getHistorialEstados(), service.obtenerHistorial(pedido.getId()));
    }

    @Test
    void cadaTransicionValidaPuedeEjecutarseDesdeSuEstadoInicial() {
        Pedido recibido = crearPedido();
        recibido.setConfirmado(true);
        assertEquals(EstadoPedido.EN_PREPARACION,
                service.cambiarEstado(recibido.getId(), EstadoPedido.EN_PREPARACION, "usuario").getEstado());

        Pedido enPreparacion = crearPedido();
        enPreparacion.setEstado(EstadoPedido.EN_PREPARACION);
        assertEquals(EstadoPedido.LISTO,
                service.cambiarEstado(enPreparacion.getId(), EstadoPedido.LISTO, "usuario").getEstado());

        Pedido listo = crearPedido();
        listo.setEstado(EstadoPedido.LISTO);
        assertEquals(EstadoPedido.ENTREGADO,
                service.cambiarEstado(listo.getId(), EstadoPedido.ENTREGADO, "usuario").getEstado());
    }

    @ParameterizedTest
    @MethodSource("transicionesInvalidas")
    void transicionesInvalidasNoModificanEstadoNiHistorial(
            EstadoPedido estadoAnterior, EstadoPedido estadoNuevo) {
        Pedido pedido = crearPedido();
        pedido.setEstado(estadoAnterior);
        pedido.setConfirmado(true);

        assertThrows(InvalidOrderStateException.class,
                () -> service.cambiarEstado(pedido.getId(), estadoNuevo, "usuario"));

        assertEquals(estadoAnterior, pedido.getEstado());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    static Stream<Arguments> transicionesInvalidas() {
        return Stream.of(
                Arguments.of(EstadoPedido.RECIBIDO, EstadoPedido.LISTO),
                Arguments.of(EstadoPedido.RECIBIDO, EstadoPedido.ENTREGADO),
                Arguments.of(EstadoPedido.EN_PREPARACION, EstadoPedido.ENTREGADO),
                Arguments.of(EstadoPedido.LISTO, EstadoPedido.EN_PREPARACION),
                Arguments.of(EstadoPedido.ENTREGADO, EstadoPedido.RECIBIDO),
                Arguments.of(EstadoPedido.ENTREGADO, EstadoPedido.EN_PREPARACION),
                Arguments.of(EstadoPedido.ENTREGADO, EstadoPedido.LISTO),
                Arguments.of(EstadoPedido.ENTREGADO, EstadoPedido.ENTREGADO),
                Arguments.of(EstadoPedido.RECIBIDO, EstadoPedido.RECIBIDO));
    }

    @Test
    void recibidoAListoFallidoConservaExplicitamentePedidoIntacto() {
        Pedido pedido = crearPedido();
        pedido.setConfirmado(true);

        assertThrows(InvalidOrderStateException.class,
                () -> service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "usuario"));

        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    @Test
    void pedidoNoConfirmadoNoPuedeEntrarEnPreparacion() {
        Pedido pedido = crearPedido();

        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(
                        pedido.getId(), EstadoPedido.EN_PREPARACION, "usuario"));
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    @Test
    void usuarioResponsableInvalidoNoPermiteCambiarEstado() {
        Pedido pedido = crearPedido();
        pedido.setConfirmado(true);

        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(
                        pedido.getId(), EstadoPedido.EN_PREPARACION, null));
        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(
                        pedido.getId(), EstadoPedido.EN_PREPARACION, "   "));
        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(
                        pedido.getId(), EstadoPedido.EN_PREPARACION, "a".repeat(101)));
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertTrue(pedido.getHistorialEstados().isEmpty());
    }

    @Test
    void pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion() {
        Pedido pedido = pedidoConItem(false);
        ItemPedido item = pedido.getItems().getFirst();
        service.confirmar(pedido.getId());

        service.actualizarCantidadItem(pedido.getId(), item.getId(), 4);
        assertEquals(4, item.getCantidad());

        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        assertThrows(InvalidOrderStateException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), item.getId(), 5));
        assertEquals(4, item.getCantidad());
    }

    @Test
    void confirmacionesConcurrentesSoloPermitenUnaConfirmacion() throws Exception {
        Pedido pedido = pedidoConItem(false);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger rechazos = new AtomicInteger();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            var tarea = (java.util.concurrent.Callable<Void>) () -> {
                inicio.await();
                try {
                    service.confirmar(pedido.getId());
                    exitos.incrementAndGet();
                } catch (BusinessRuleException exception) {
                    rechazos.incrementAndGet();
                }
                return null;
            };
            var primero = ejecutor.submit(tarea);
            var segundo = ejecutor.submit(tarea);
            inicio.countDown();
            primero.get();
            segundo.get();
        }

        assertEquals(1, exitos.get());
        assertEquals(1, rechazos.get());
        assertTrue(pedido.isConfirmado());
    }

    @Test
    void transicionesConcurrentesDesdeRecibidoSoloRegistranUna() throws Exception {
        Pedido pedido = crearPedido();
        pedido.setConfirmado(true);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger rechazos = new AtomicInteger();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            var tarea = (java.util.concurrent.Callable<Void>) () -> {
                inicio.await();
                try {
                    service.cambiarEstado(
                            pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
                    exitos.incrementAndGet();
                } catch (InvalidOrderStateException exception) {
                    rechazos.incrementAndGet();
                }
                return null;
            };
            var primero = ejecutor.submit(tarea);
            var segundo = ejecutor.submit(tarea);
            inicio.countDown();
            primero.get();
            segundo.get();
        }

        assertEquals(1, exitos.get());
        assertEquals(1, rechazos.get());
        assertEquals(1, pedido.getHistorialEstados().size());
    }

    @Test
    void agregarMismoPlatoConcurrentementeMantieneUnSoloItem() throws Exception {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(30L, "Hamburguesa", "20.00", false);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            var primero = ejecutor.submit(() -> {
                agregarTrasSenal(inicio, pedido, plato);
                return null;
            });
            var segundo = ejecutor.submit(() -> {
                agregarTrasSenal(inicio, pedido, plato);
                return null;
            });
            inicio.countDown();
            primero.get();
            segundo.get();
        }

        assertEquals(1, pedido.getItems().size());
        assertEquals(2, pedido.getItems().getFirst().getCantidad());
    }

    private void agregarTrasSenal(CountDownLatch inicio, Pedido pedido, Plato plato)
            throws InterruptedException {
        inicio.await();
        service.agregarItem(pedido.getId(), plato.getId(), 1);
    }

    private Pedido pedidoConItem(boolean combo) {
        Pedido pedido = crearPedido();
        Plato plato = platoDisponible(combo ? 31L : 30L, combo ? "Combo" : "Plato", "20.00", combo);
        when(platoService.obtenerPorId(plato.getId())).thenReturn(plato);
        service.agregarItem(pedido.getId(), plato.getId(), 2);
        return pedido;
    }

    private Pedido crearPedido() {
        cuentaExistente();
        return service.crear(cuenta.getId());
    }

    private void cuentaExistente() {
        when(cuentaService.obtenerPorId(cuenta.getId())).thenReturn(cuenta);
    }

    private Plato platoDisponible(Long id, String nombre, String precio, boolean combo) {
        return Plato.builder()
                .id(id)
                .nombre(nombre)
                .precio(new BigDecimal(precio))
                .activo(true)
                .combo(combo)
                .ingredientes(new ArrayList<>())
                .build();
    }
}
