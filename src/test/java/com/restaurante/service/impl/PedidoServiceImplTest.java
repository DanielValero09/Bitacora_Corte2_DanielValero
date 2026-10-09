package com.restaurante.service.impl;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.service.PlatoService;
import com.restaurante.service.auditoria.CambioEstadoPedidoAuditEvent;
import com.restaurante.support.RelationalTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PedidoServiceImplTest {
    @Mock
    private PlatoService platoService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private RelationalTestFixture persistence;
    private PedidoServiceImpl service;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        persistence = new RelationalTestFixture(platoService, eventPublisher);
        service = persistence.pedidos();
        Mesa mesa = persistence.mesas().crear(Mesa.builder().numero(1).build());
        cuenta = persistence.cuentas().abrirCuenta(mesa.getId());
    }

    @Test
    void crearPedidoInicializaEstadoPersistente() {
        Pedido pedido = service.crear(cuenta.getId());

        assertNotNull(pedido.getId());
        assertEquals(cuenta.getId(), pedido.getCuentaId());
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertFalse(pedido.isConfirmado());
        assertNotNull(pedido.getFechaCreacion());
        assertNull(pedido.getFechaConfirmacion());
        assertTrue(pedido.getItems().isEmpty());
        assertTrue(service.obtenerPorId(pedido.getId()).getHistorialEstados().isEmpty());
    }

    @Test
    void cuentaCerradaImpideCrearPedido() {
        persistence.pagos().registrarPago(cuenta.getId());
        assertThrows(BusinessRuleException.class, () -> service.crear(cuenta.getId()));
    }

    @Test
    void pedidoYCuentaInexistentesProducenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.crear(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
        assertThrows(ResourceNotFoundException.class, () -> service.listarPorCuenta(99L));
    }

    @Test
    void listasVaciasYFiltradoPorCuenta() {
        assertTrue(service.listar().isEmpty());
        Pedido pedido = service.crear(cuenta.getId());
        assertEquals(List.of(pedido.getId()),
                service.listarPorCuenta(cuenta.getId()).stream().map(Pedido::getId).toList());
    }

    @Test
    void agregarItemCreaSnapshotYAcumulaCantidad() {
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());

        service.agregarItem(pedido.getId(), 7L, 2);
        Pedido actualizado = service.agregarItem(pedido.getId(), 7L, 1);
        ItemPedido item = actualizado.getItems().getFirst();

        assertNotNull(item.getId());
        assertEquals(7L, item.getPlatoId());
        assertEquals("Hamburguesa", item.getNombrePlato());
        assertEquals(new BigDecimal("20.00"), item.getPrecioCongelado());
        assertEquals(3, item.getCantidad());
        assertFalse(item.isCombo());
        assertFalse(item.isBebidaIncluida());
    }

    @Test
    void precioCongeladoNoCambiaConElPlato() {
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);

        plato.setPrecio(new BigDecimal("35.00"));

        Pedido persistido = service.obtenerPorId(pedido.getId());
        assertEquals(new BigDecimal("20.00"), persistido.getItems().getFirst().getPrecioCongelado());
        assertEquals(new BigDecimal("20.00"), persistido.calcularTotal());
    }

    @Test
    void cantidadInvalidaYPlatoNoDisponibleSeRechazan() {
        Pedido pedido = service.crear(cuenta.getId());
        assertThrows(BusinessRuleException.class, () -> service.agregarItem(pedido.getId(), 7L, 0));
        when(platoService.obtenerPorId(7L)).thenReturn(
                plato(7L, "Agotado", "20.00", false, false));
        assertThrows(BusinessRuleException.class, () -> service.agregarItem(pedido.getId(), 7L, 1));
    }

    @Test
    void actualizarEliminarYRetirarBebidaPersisten() {
        when(platoService.obtenerPorId(7L)).thenReturn(
                plato(7L, "Combo", "25.00", true, true));
        Pedido pedido = service.crear(cuenta.getId());
        ItemPedido item = service.agregarItem(pedido.getId(), 7L, 1).getItems().getFirst();

        Pedido actualizado = service.actualizarCantidadItem(pedido.getId(), item.getId(), 3);
        assertEquals(3, actualizado.getItems().getFirst().getCantidad());
        actualizado = service.retirarBebidaCombo(pedido.getId(), item.getId());
        assertFalse(actualizado.getItems().getFirst().isBebidaIncluida());
        assertEquals(new BigDecimal("75.00"), actualizado.calcularTotal());

        service.eliminarItem(pedido.getId(), item.getId());
        assertTrue(service.obtenerPorId(pedido.getId()).getItems().isEmpty());
    }

    @Test
    void retirarBebidaDeNoComboEItemInexistenteSeRechazan() {
        when(platoService.obtenerPorId(7L)).thenReturn(
                plato(7L, "Hamburguesa", "20.00", false, true));
        Pedido pedido = service.crear(cuenta.getId());
        ItemPedido item = service.agregarItem(pedido.getId(), 7L, 1).getItems().getFirst();
        assertThrows(BusinessRuleException.class,
                () -> service.retirarBebidaCombo(pedido.getId(), item.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), 99L, 1));
    }

    @Test
    void confirmarRequiereItemsDisponiblesYNoPermiteRepeticion() {
        Pedido vacio = service.crear(cuenta.getId());
        assertThrows(BusinessRuleException.class, () -> service.confirmar(vacio.getId()));

        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        service.agregarItem(vacio.getId(), 7L, 1);
        Pedido confirmado = service.confirmar(vacio.getId());
        assertTrue(confirmado.isConfirmado());
        assertNotNull(confirmado.getFechaConfirmacion());
        assertThrows(BusinessRuleException.class, () -> service.confirmar(vacio.getId()));
    }

    @Test
    void cocinaSoloListaConfirmadosNoEntregados() {
        Pedido pedido = pedidoConfirmado();
        assertEquals(List.of(pedido.getId()),
                service.listarParaCocina().stream().map(Pedido::getId).toList());
        completarCocina(pedido.getId());
        assertTrue(service.listarParaCocina().isEmpty());
    }

    @Test
    void transicionesValidasPersistenHistorialCompletoRn07() {
        Pedido pedido = pedidoConfirmado();
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero-1");
        service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero-2");
        service.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero-1");

        List<CambioEstadoPedido> historial = service.obtenerHistorial(pedido.getId());
        assertEquals(3, historial.size());
        assertEquals(EstadoPedido.RECIBIDO, historial.get(0).getEstadoAnterior());
        assertEquals(EstadoPedido.EN_PREPARACION, historial.get(0).getEstadoNuevo());
        assertEquals("cocinero-1", historial.get(0).getUsuarioResponsable());
        assertNotNull(historial.get(0).getFechaHora());
        assertEquals(EstadoPedido.EN_PREPARACION, historial.get(1).getEstadoAnterior());
        assertEquals(EstadoPedido.LISTO, historial.get(1).getEstadoNuevo());
        assertEquals("cocinero-2", historial.get(1).getUsuarioResponsable());
        assertNotNull(historial.get(1).getFechaHora());
        assertEquals(EstadoPedido.LISTO, historial.get(2).getEstadoAnterior());
        assertEquals(EstadoPedido.ENTREGADO, historial.get(2).getEstadoNuevo());
        assertEquals("mesero-1", historial.get(2).getUsuarioResponsable());
        assertNotNull(historial.get(2).getFechaHora());
        assertEquals(EstadoPedido.ENTREGADO, service.obtenerPorId(pedido.getId()).getEstado());
    }

    @Test
    void transicionInvalidaNoConfirmadoYUsuarioInvalidoSeRechazan() {
        Pedido pedido = service.crear(cuenta.getId());
        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(
                        pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero"));
        assertThrows(InvalidOrderStateException.class,
                () -> service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero"));
        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, " "));
        assertTrue(service.obtenerHistorial(pedido.getId()).isEmpty());
    }

    @Test
    void desdePreparacionContenidoEsInmutable() {
        Pedido pedido = pedidoConfirmado();
        Long itemId = pedido.getItems().getFirst().getId();
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");

        assertThrows(InvalidOrderStateException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), itemId, 2));
        assertThrows(InvalidOrderStateException.class,
                () -> service.eliminarItem(pedido.getId(), itemId));
        assertThrows(InvalidOrderStateException.class,
                () -> service.retirarBebidaCombo(pedido.getId(), itemId));
    }

    @Test
    void pagoBloqueaContenidoPeroNoFlujoDeCocina() {
        Pedido pedido = pedidoConfirmado();
        persistence.pagos().registrarPago(cuenta.getId());

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), 7L, 1));
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero");
        assertEquals(EstadoPedido.LISTO, service.obtenerPorId(pedido.getId()).getEstado());
    }

    @Test
    void obtenerPedidoPersistidoDevuelveSuEstadoActual() {
        Pedido creado = service.crear(cuenta.getId());

        Pedido recuperado = service.obtenerPorId(creado.getId());

        assertEquals(creado.getId(), recuperado.getId());
        assertEquals(EstadoPedido.RECIBIDO, recuperado.getEstado());
    }

    @Test
    void listarPedidosConservaOrdenAscendenteDelRepository() {
        Pedido primero = service.crear(cuenta.getId());
        Pedido segundo = service.crear(cuenta.getId());

        assertEquals(List.of(primero.getId(), segundo.getId()),
                service.listar().stream().map(Pedido::getId).toList());
    }

    @Test
    void listarPorCuentaNoMezclaPedidosDeOtraCuenta() {
        Pedido esperado = service.crear(cuenta.getId());
        Mesa otraMesa = persistence.mesas().crear(Mesa.builder().numero(2).build());
        Cuenta otraCuenta = persistence.cuentas().abrirCuenta(otraMesa.getId());
        service.crear(otraCuenta.getId());

        assertEquals(List.of(esperado.getId()),
                service.listarPorCuenta(cuenta.getId()).stream().map(Pedido::getId).toList());
    }

    @Test
    void platoInexistenteImpideAgregarItem() {
        Pedido pedido = service.crear(cuenta.getId());
        when(platoService.obtenerPorId(99L))
                .thenThrow(new ResourceNotFoundException("No existe el plato"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.agregarItem(pedido.getId(), 99L, 1));
        assertTrue(service.obtenerPorId(pedido.getId()).getItems().isEmpty());
    }

    @Test
    void platoInactivoImpideAgregarItem() {
        Pedido pedido = service.crear(cuenta.getId());
        when(platoService.obtenerPorId(7L)).thenReturn(
                plato(7L, "Inactivo", "20.00", false, false));

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), 7L, 1));
    }

    @Test
    void ingredienteAgotadoImpideAgregarItem() {
        Pedido pedido = service.crear(cuenta.getId());
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        plato.setIngredientes(List.of(
                Ingrediente.builder().id(1L).nombre("Pan").disponible(false).build()));
        when(platoService.obtenerPorId(7L)).thenReturn(plato);

        assertThrows(BusinessRuleException.class,
                () -> service.agregarItem(pedido.getId(), 7L, 1));
    }

    @Test
    void cuentaCerradaImpideModificarContenido() {
        Pedido pedido = pedidoConItem(7L, false);
        Long itemId = pedido.getItems().getFirst().getId();
        persistence.pagos().registrarPago(cuenta.getId());

        assertThrows(BusinessRuleException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), itemId, 2));
    }

    @Test
    void estadoDistintoDeRecibidoImpideAgregarItem() {
        Pedido pedido = pedidoConfirmado();
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");

        assertThrows(InvalidOrderStateException.class,
                () -> service.agregarItem(pedido.getId(), 7L, 1));
    }

    @Test
    void comboIniciaConBebidaIncluida() {
        Pedido pedido = pedidoConItem(7L, true);
        ItemPedido item = pedido.getItems().getFirst();

        assertTrue(item.isCombo());
        assertTrue(item.isBebidaIncluida());
    }

    @Test
    void productoRepetidoConservaSnapshotOriginal() {
        Plato plato = plato(7L, "Original", "20000.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        plato.setNombre("Renombrado");
        plato.setPrecio(new BigDecimal("30000.00"));
        plato.setCombo(true);

        ItemPedido item = service.agregarItem(pedido.getId(), 7L, 2).getItems().getFirst();

        assertEquals("Original", item.getNombrePlato());
        assertEquals(new BigDecimal("20000.00"), item.getPrecioCongelado());
        assertEquals(3, item.getCantidad());
        assertEquals(1, service.obtenerPorId(pedido.getId()).getItems().size());
        assertFalse(item.isCombo());
        assertFalse(item.isBebidaIncluida());
    }

    @Test
    void totalDeCuentaReflejaItemsPersistidos() {
        Pedido pedido = pedidoConItem(7L, false);
        service.actualizarCantidadItem(
                pedido.getId(), pedido.getItems().getFirst().getId(), 2);

        assertEquals(new BigDecimal("40.00"),
                persistence.cuentas().obtenerPorId(cuenta.getId()).calcularTotal());
    }

    @Test
    void cantidadInvalidaImpideActualizarItem() {
        Pedido pedido = pedidoConItem(7L, false);
        Long itemId = pedido.getItems().getFirst().getId();

        assertThrows(BusinessRuleException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), itemId, 0));
        assertEquals(1, service.obtenerPorId(pedido.getId()).getItems().getFirst().getCantidad());
    }

    @Test
    void itemInexistenteImpideEliminar() {
        Pedido pedido = service.crear(cuenta.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> service.eliminarItem(pedido.getId(), 99L));
    }

    @Test
    void itemInexistenteImpideRetirarBebida() {
        Pedido pedido = service.crear(cuenta.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> service.retirarBebidaCombo(pedido.getId(), 99L));
    }

    @Test
    void eliminarItemNoEliminaElPedido() {
        Pedido pedido = pedidoConItem(7L, false);
        service.eliminarItem(pedido.getId(), pedido.getItems().getFirst().getId());

        Pedido persistido = service.obtenerPorId(pedido.getId());
        assertEquals(pedido.getId(), persistido.getId());
        assertTrue(persistido.getItems().isEmpty());
    }

    @Test
    void retirarBebidaEsIdempotenteYNoCambiaPrecio() {
        Pedido pedido = pedidoConItem(7L, true);
        Long itemId = pedido.getItems().getFirst().getId();

        service.retirarBebidaCombo(pedido.getId(), itemId);
        ItemPedido item = service.retirarBebidaCombo(pedido.getId(), itemId)
                .getItems().getFirst();

        assertFalse(item.isBebidaIncluida());
        assertEquals(1, item.getCantidad());
        assertEquals(new BigDecimal("20.00"), item.getPrecioCongelado());
        assertEquals(new BigDecimal("20.00"), item.calcularSubtotal());
    }

    @Test
    void confirmarRegistraFechaSinCambiarEstadoNiHistorial() {
        Pedido pedido = pedidoConItem(7L, false);

        Pedido confirmado = service.confirmar(pedido.getId());

        assertTrue(confirmado.isConfirmado());
        assertNotNull(confirmado.getFechaConfirmacion());
        assertEquals(EstadoPedido.RECIBIDO, confirmado.getEstado());
        assertTrue(confirmado.getHistorialEstados().isEmpty());
    }

    @Test
    void segundaConfirmacionNoAlteraLaFechaOriginal() {
        Pedido confirmado = pedidoConfirmado();
        var fechaOriginal = confirmado.getFechaConfirmacion();

        assertThrows(BusinessRuleException.class, () -> service.confirmar(confirmado.getId()));
        assertEquals(fechaOriginal, service.obtenerPorId(confirmado.getId()).getFechaConfirmacion());
    }

    @Test
    void cuentaCerradaImpideConfirmar() {
        Pedido pedido = pedidoConItem(7L, false);
        persistence.pagos().registrarPago(cuenta.getId());

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));
    }

    @Test
    void estadoDistintoDeRecibidoImpideConfirmar() {
        Pedido pedido = pedidoConfirmado();
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");

        assertThrows(InvalidOrderStateException.class, () -> service.confirmar(pedido.getId()));
    }

    @Test
    void productoAgotadoDespuesDeAgregarseImpideConfirmar() {
        Ingrediente ingrediente = Ingrediente.builder().id(1L).nombre("Pan")
                .disponible(true).build();
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        plato.setIngredientes(List.of(ingrediente));
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        ingrediente.setDisponible(false);

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));
    }

    @Test
    void platoEliminadoAntesDeConfirmarPropagaNotFound() {
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        when(platoService.obtenerPorId(7L))
                .thenThrow(new ResourceNotFoundException("No existe el plato"));

        assertThrows(ResourceNotFoundException.class, () -> service.confirmar(pedido.getId()));
    }

    @Test
    void cambioDePrecioNoModificaSnapshotAlConfirmar() {
        Plato plato = plato(7L, "Hamburguesa", "20000.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        plato.setPrecio(new BigDecimal("30000.00"));

        Pedido confirmado = service.confirmar(pedido.getId());

        assertEquals(new BigDecimal("20000.00"), confirmado.calcularTotal());
    }

    @Test
    void todosLosProductosDisponiblesPermitenConfirmar() {
        Plato primero = plato(7L, "Hamburguesa", "20.00", false, true);
        Plato segundo = plato(8L, "Papas", "10.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(primero);
        when(platoService.obtenerPorId(8L)).thenReturn(segundo);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        service.agregarItem(pedido.getId(), 8L, 2);

        assertTrue(service.confirmar(pedido.getId()).isConfirmado());
    }

    @Test
    void tableroCocinaVacioSinPedidosElegibles() {
        service.crear(cuenta.getId());

        assertTrue(service.listarParaCocina().isEmpty());
    }

    @Test
    void tableroIncluyeConfirmadosActivosYExcluyeLosDemas() {
        Pedido activo = pedidoConfirmado();
        Pedido enPreparacion = pedidoConfirmado();
        service.cambiarEstado(enPreparacion.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        Pedido listo = pedidoConfirmado();
        service.cambiarEstado(listo.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        service.cambiarEstado(listo.getId(), EstadoPedido.LISTO, "cocinero");
        service.crear(cuenta.getId());
        Pedido entregado = pedidoConfirmado();
        completarCocina(entregado.getId());

        assertEquals(List.of(activo.getId(), enPreparacion.getId(), listo.getId()),
                service.listarParaCocina().stream().map(Pedido::getId).toList());
    }

    @Test
    void pedidoConfirmadoSigueEditableHastaEntrarEnPreparacion() {
        Pedido pedido = pedidoConfirmado();
        Long itemId = pedido.getItems().getFirst().getId();

        assertEquals(4, service.actualizarCantidadItem(pedido.getId(), itemId, 4)
                .getItems().getFirst().getCantidad());
        service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        assertThrows(InvalidOrderStateException.class,
                () -> service.actualizarCantidadItem(pedido.getId(), itemId, 5));
        assertEquals(4, service.obtenerPorId(pedido.getId()).getItems().getFirst().getCantidad());
    }

    @Test
    void platoDesactivadoDespuesDeAgregarseImpideConfirmarSinCambiosParciales() {
        Plato plato = plato(7L, "Hamburguesa", "20.00", false, true);
        when(platoService.obtenerPorId(7L)).thenReturn(plato);
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        plato.setActivo(false);

        assertThrows(BusinessRuleException.class, () -> service.confirmar(pedido.getId()));
        Pedido persistido = service.obtenerPorId(pedido.getId());
        assertFalse(persistido.isConfirmado());
        assertNull(persistido.getFechaConfirmacion());
        assertTrue(persistido.getHistorialEstados().isEmpty());
    }

    @Test
    void usuarioResponsableNuloNoRegistraTransicion() {
        Pedido pedido = pedidoConfirmado();

        assertThrows(BusinessRuleException.class,
                () -> service.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, null));
        assertTrue(service.obtenerHistorial(pedido.getId()).isEmpty());
    }

    @Test
    void usuarioResponsableMayorACienCaracteresNoRegistraTransicion() {
        Pedido pedido = pedidoConfirmado();

        assertThrows(BusinessRuleException.class, () -> service.cambiarEstado(
                pedido.getId(), EstadoPedido.EN_PREPARACION, "x".repeat(101)));
        assertTrue(service.obtenerHistorial(pedido.getId()).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("transicionesInvalidas")
    void transicionesInvalidasNoModificanEstadoNiHistorial(
            EstadoPedido estadoInicial, EstadoPedido estadoNuevo) {
        Pedido pedido = pedidoEnEstado(estadoInicial);
        int cambiosPrevios = service.obtenerHistorial(pedido.getId()).size();
        clearInvocations(eventPublisher);

        assertThrows(InvalidOrderStateException.class,
                () -> service.cambiarEstado(pedido.getId(), estadoNuevo, "responsable"));

        Pedido persistido = service.obtenerPorId(pedido.getId());
        assertEquals(estadoInicial, persistido.getEstado());
        assertEquals(cambiosPrevios, persistido.getHistorialEstados().size());
        verifyNoInteractions(eventPublisher);
    }

    @ParameterizedTest
    @MethodSource("transicionesValidasAuditoria")
    void transicionValidaPublicaExactamenteUnEventoConDatosDelHistorial(
            EstadoPedido anterior, EstadoPedido nuevo) {
        Pedido pedido = pedidoEnEstado(anterior);
        clearInvocations(eventPublisher);

        Pedido actualizado = service.cambiarEstado(pedido.getId(), nuevo, "responsable");

        ArgumentCaptor<CambioEstadoPedidoAuditEvent> captor =
                ArgumentCaptor.forClass(CambioEstadoPedidoAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        verifyNoMoreInteractions(eventPublisher);
        CambioEstadoPedidoAuditEvent evento = captor.getValue();
        CambioEstadoPedido historial = actualizado.getHistorialEstados().getLast();
        assertEquals(pedido.getId(), evento.pedidoId());
        assertEquals(anterior, evento.estadoAnterior());
        assertEquals(nuevo, evento.estadoNuevo());
        assertEquals(historial.getUsuarioResponsable(), evento.usuarioResponsable());
        assertEquals(historial.getFechaHora(), evento.fechaHora());
        assertEquals(nuevo, service.obtenerPorId(pedido.getId()).getEstado());
    }

    @Test
    void pedidoSinConfirmarNoPublicaAuditoria() {
        Pedido pedido = service.crear(cuenta.getId());

        assertThrows(BusinessRuleException.class, () -> service.cambiarEstado(
                pedido.getId(), EstadoPedido.EN_PREPARACION, "responsable"));

        verifyNoInteractions(eventPublisher);
        assertTrue(service.obtenerHistorial(pedido.getId()).isEmpty());
    }

    private static Stream<Arguments> transicionesValidasAuditoria() {
        return Stream.of(
                Arguments.of(EstadoPedido.RECIBIDO, EstadoPedido.EN_PREPARACION),
                Arguments.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO),
                Arguments.of(EstadoPedido.LISTO, EstadoPedido.ENTREGADO));
    }

    @Test
    void recibidoAListoFallidoConservaPedidoIntacto() {
        Pedido pedido = pedidoConfirmado();

        assertThrows(InvalidOrderStateException.class,
                () -> service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero"));

        Pedido persistido = service.obtenerPorId(pedido.getId());
        assertEquals(EstadoPedido.RECIBIDO, persistido.getEstado());
        assertTrue(persistido.getHistorialEstados().isEmpty());
    }

    private Pedido pedidoConfirmado() {
        when(platoService.obtenerPorId(7L)).thenReturn(
                plato(7L, "Combo", "25.00", true, true));
        Pedido pedido = service.crear(cuenta.getId());
        service.agregarItem(pedido.getId(), 7L, 1);
        return service.confirmar(pedido.getId());
    }

    private Pedido pedidoConItem(Long platoId, boolean combo) {
        when(platoService.obtenerPorId(platoId)).thenReturn(
                plato(platoId, combo ? "Combo" : "Hamburguesa", "20.00", combo, true));
        Pedido pedido = service.crear(cuenta.getId());
        return service.agregarItem(pedido.getId(), platoId, 1);
    }

    private Pedido pedidoEnEstado(EstadoPedido estado) {
        Pedido pedido = pedidoConfirmado();
        if (estado == EstadoPedido.RECIBIDO) {
            return pedido;
        }
        pedido = service.cambiarEstado(
                pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero-1");
        if (estado == EstadoPedido.EN_PREPARACION) {
            return pedido;
        }
        pedido = service.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero-2");
        if (estado == EstadoPedido.LISTO) {
            return pedido;
        }
        return service.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero-1");
    }

    private static Stream<Arguments> transicionesInvalidas() {
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

    private void completarCocina(Long pedidoId) {
        service.cambiarEstado(pedidoId, EstadoPedido.EN_PREPARACION, "cocinero");
        service.cambiarEstado(pedidoId, EstadoPedido.LISTO, "cocinero");
        service.cambiarEstado(pedidoId, EstadoPedido.ENTREGADO, "mesero");
    }

    private Plato plato(Long id, String nombre, String precio, boolean combo, boolean activo) {
        return Plato.builder().id(id).nombre(nombre).precio(new BigDecimal(precio))
                .combo(combo).activo(activo).ingredientes(List.of()).build();
    }
}
