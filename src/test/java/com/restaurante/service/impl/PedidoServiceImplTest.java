package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.exception.ResourceNotFoundException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
