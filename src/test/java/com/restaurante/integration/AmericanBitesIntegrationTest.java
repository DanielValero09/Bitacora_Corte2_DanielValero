package com.restaurante.integration;

import com.restaurante.exception.BusinessRuleException;
import com.restaurante.exception.InvalidOrderStateException;
import com.restaurante.model.domain.CambioEstadoPedido;
import com.restaurante.model.domain.Cuenta;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.ItemPedido;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.domain.Pago;
import com.restaurante.model.domain.Pedido;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.service.impl.CuentaServiceImpl;
import com.restaurante.service.impl.IngredienteServiceImpl;
import com.restaurante.service.impl.MesaServiceImpl;
import com.restaurante.service.impl.PagoServiceImpl;
import com.restaurante.service.impl.PedidoServiceImpl;
import com.restaurante.service.impl.PlatoServiceImpl;
import com.restaurante.support.CatalogoTestFixture;
import com.restaurante.support.RelationalTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmericanBitesIntegrationTest {
    private IngredienteServiceImpl ingredientes;
    private PlatoServiceImpl platos;
    private MesaServiceImpl mesas;
    private CuentaServiceImpl cuentas;
    private PedidoServiceImpl pedidos;
    private PagoServiceImpl pagos;
    private Cuenta cuenta;

    @BeforeEach
    void setUp() {
        CatalogoTestFixture catalogo = new CatalogoTestFixture();
        ingredientes = catalogo.ingredientes();
        platos = catalogo.platos();
        RelationalTestFixture persistence = new RelationalTestFixture(platos);
        mesas = persistence.mesas();
        cuentas = persistence.cuentas();
        pedidos = persistence.pedidos();
        pagos = persistence.pagos();
        Mesa mesa = mesas.crear(Mesa.builder().numero(1).build());
        cuenta = cuentas.abrirCuenta(mesa.getId());
    }

    @Test
    void retirarBebidaDeComboConservaTodosLosValoresEconomicosHastaElPago() {
        Ingrediente ingrediente = crearIngrediente("Carne", true);
        Plato combo = crearPlato("Combo clasico", "25.00", true, ingrediente);
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedido = pedidos.agregarItem(pedido.getId(), combo.getId(), 2);
        ItemPedido item = pedido.getItems().getFirst();
        BigDecimal precio = item.getPrecioCongelado();
        BigDecimal subtotal = item.calcularSubtotal();
        BigDecimal totalPedido = pedido.calcularTotal();
        BigDecimal totalCuenta = cuentas.obtenerPorId(cuenta.getId()).calcularTotal();

        assertAll(
                () -> assertTrue(item.isCombo()),
                () -> assertTrue(item.isBebidaIncluida()));

        Pedido sinBebida = pedidos.retirarBebidaCombo(pedido.getId(), item.getId());
        ItemPedido itemSinBebida = sinBebida.getItems().getFirst();

        assertAll(
                () -> assertFalse(itemSinBebida.isBebidaIncluida()),
                () -> assertEquals(precio, itemSinBebida.getPrecioCongelado()),
                () -> assertEquals(subtotal, itemSinBebida.calcularSubtotal()),
                () -> assertEquals(totalPedido, sinBebida.calcularTotal()),
                () -> assertEquals(totalCuenta,
                        cuentas.obtenerPorId(cuenta.getId()).calcularTotal()));

        pedidos.confirmar(pedido.getId());
        completarFlujoCocina(pedido);
        Pago pago = pagos.registrarPago(cuenta.getId());

        assertEquals(totalCuenta, pago.getMonto());
    }

    @Test
    void retirarBebidaRechazaNoComboYPedidoEnPreparacion() {
        Plato plato = crearPlato("Hamburguesa", "20.00", false,
                crearIngrediente("Pan", true));
        Plato combo = crearPlato("Combo pollo", "24.00", true,
                crearIngrediente("Pollo", true));
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedido.getId(), plato.getId(), 1);
        pedido = pedidos.agregarItem(pedido.getId(), combo.getId(), 1);
        ItemPedido noCombo = pedido.getItems().get(0);
        ItemPedido itemCombo = pedido.getItems().get(1);
        Long pedidoId = pedido.getId();

        assertThrows(BusinessRuleException.class,
                () -> pedidos.retirarBebidaCombo(pedidoId, noCombo.getId()));

        pedidos.confirmar(pedidoId);
        pedidos.cambiarEstado(pedidoId, EstadoPedido.EN_PREPARACION, "cocinero");

        assertThrows(InvalidOrderStateException.class,
                () -> pedidos.retirarBebidaCombo(pedidoId, itemCombo.getId()));
        assertTrue(itemCombo.isBebidaIncluida());
    }

    @Test
    void precioCongeladoSeConservaDeExtremoAExtremoAunqueCambieElPlato() {
        Ingrediente ingrediente = crearIngrediente("Queso", true);
        Plato plato = crearPlato("Hamburguesa", "20.00", false, ingrediente);
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedido = pedidos.agregarItem(pedido.getId(), plato.getId(), 1);
        Pedido pedidoConPrecioCongelado = pedido;
        ItemPedido item = pedido.getItems().getFirst();

        platos.actualizar(plato.getId(), Plato.builder()
                .nombre(plato.getNombre())
                .descripcion(plato.getDescripcion())
                .precio(new BigDecimal("30.00"))
                .combo(false)
                .build(), List.of(ingrediente.getId()));
        pedidos.confirmar(pedido.getId());
        completarFlujoCocina(pedido);
        Pago pago = pagos.registrarPago(cuenta.getId());

        assertAll(
                () -> assertEquals(new BigDecimal("30.00"),
                        platos.obtenerPorId(plato.getId()).getPrecio()),
                () -> assertEquals(new BigDecimal("20.00"), item.getPrecioCongelado()),
                () -> assertEquals(new BigDecimal("20.00"),
                        pedidoConPrecioCongelado.calcularTotal()),
                () -> assertEquals(new BigDecimal("20.00"),
                        cuentas.obtenerPorId(cuenta.getId()).calcularTotal()),
                () -> assertEquals(new BigDecimal("20.00"), pago.getMonto()));
    }

    @Test
    void disponibilidadCompartidaMantienePlatoEnCartaPeroImpideAgregarlo() {
        Ingrediente ingrediente = crearIngrediente("Tocineta", true);
        Plato plato = crearPlato("Hamburguesa especial", "22.00", false, ingrediente);
        Pedido pedido = pedidos.crear(cuenta.getId());

        assertTrue(plato.isDisponible());

        ingredientes.cambiarDisponibilidad(ingrediente.getId(), false);
        Plato platoActualizado = platos.obtenerPorId(plato.getId());

        assertAll(
                () -> assertFalse(platoActualizado.isDisponible()),
                () -> assertEquals(List.of(plato.getId()),
                        platos.listarCarta().stream().map(Plato::getId).toList()),
                () -> assertEquals(ingrediente.getId(),
                        platoActualizado.getIngredientes().getFirst().getId()),
                () -> assertThrows(BusinessRuleException.class,
                        () -> pedidos.agregarItem(pedido.getId(), plato.getId(), 1)),
                () -> assertTrue(pedido.getItems().isEmpty()));
    }

    @Test
    void agotamientoEntreAgregarYConfirmarConservaItemYPedidoRecibidoNoConfirmado() {
        Ingrediente ingrediente = crearIngrediente("Aguacate", true);
        Plato plato = crearPlato("Hamburguesa verde", "20.00", false, ingrediente);
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedido = pedidos.agregarItem(pedido.getId(), plato.getId(), 1);
        Pedido pedidoPersistido = pedido;
        Long pedidoId = pedido.getId();
        ItemPedido item = pedido.getItems().getFirst();

        ingredientes.cambiarDisponibilidad(ingrediente.getId(), false);

        assertThrows(BusinessRuleException.class, () -> pedidos.confirmar(pedidoId));
        assertAll(
                () -> assertFalse(pedidoPersistido.isConfirmado()),
                () -> assertEquals(EstadoPedido.RECIBIDO, pedidoPersistido.getEstado()),
                () -> assertEquals(1, pedidoPersistido.getItems().size()),
                () -> assertSame(item, pedidoPersistido.getItems().getFirst()),
                () -> assertEquals(new BigDecimal("20.00"), item.getPrecioCongelado()),
                () -> assertEquals(new BigDecimal("20.00"), pedidoPersistido.calcularTotal()));
    }

    @Test
    void confirmadoRecibidoPermiteTodasLasEdicionesYEnPreparacionLasBloquea() {
        Ingrediente ingrediente = crearIngrediente("Papas", true);
        Plato comboA = crearPlato("Combo A", "20.00", true, ingrediente);
        Plato comboB = crearPlato("Combo B", "22.00", true, ingrediente);
        Plato comboC = crearPlato("Combo C", "24.00", true, ingrediente);
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedido.getId(), comboA.getId(), 1);
        pedido = pedidos.agregarItem(pedido.getId(), comboB.getId(), 1);
        Long pedidoId = pedido.getId();
        ItemPedido itemA = pedido.getItems().get(0);
        ItemPedido itemB = pedido.getItems().get(1);
        pedidos.confirmar(pedidoId);

        pedidos.agregarItem(pedidoId, comboC.getId(), 1);
        pedidos.actualizarCantidadItem(pedidoId, itemA.getId(), 2);
        pedidos.retirarBebidaCombo(pedidoId, itemA.getId());
        pedidos.eliminarItem(pedidoId, itemB.getId());
        Pedido editado = pedidos.obtenerPorId(pedidoId);
        ItemPedido itemAEditado = editado.getItems().stream()
                .filter(item -> item.getId().equals(itemA.getId())).findFirst().orElseThrow();

        assertAll(
                () -> assertTrue(editado.isConfirmado()),
                () -> assertEquals(EstadoPedido.RECIBIDO, editado.getEstado()),
                () -> assertEquals(2, editado.getItems().size()),
                () -> assertEquals(2, itemAEditado.getCantidad()),
                () -> assertFalse(itemAEditado.isBebidaIncluida()),
                () -> assertFalse(editado.getItems().stream()
                        .anyMatch(item -> item.getId().equals(itemB.getId()))));

        pedidos.cambiarEstado(pedidoId, EstadoPedido.EN_PREPARACION, "cocinero");

        assertAll(
                () -> assertThrows(InvalidOrderStateException.class,
                        () -> pedidos.agregarItem(pedidoId, comboB.getId(), 1)),
                () -> assertThrows(InvalidOrderStateException.class,
                        () -> pedidos.actualizarCantidadItem(pedidoId, itemA.getId(), 3)),
                () -> assertThrows(InvalidOrderStateException.class,
                        () -> pedidos.eliminarItem(pedidoId, itemA.getId())),
                () -> assertThrows(InvalidOrderStateException.class,
                        () -> pedidos.retirarBebidaCombo(pedidoId, itemA.getId())));
    }

    @Test
    void tableroDeCocinaReflejaTodoElCicloDeVida() {
        Plato plato = crearPlato("Perro caliente", "18.00", false,
                crearIngrediente("Salchicha", true));
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedidos.agregarItem(pedido.getId(), plato.getId(), 1);

        assertFalse(contienePedido(pedidos.listarParaCocina(), pedido.getId()));

        pedidos.confirmar(pedido.getId());
        assertTrue(contienePedido(pedidos.listarParaCocina(), pedido.getId()));
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        assertTrue(contienePedido(pedidos.listarParaCocina(), pedido.getId()));
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero");
        assertTrue(contienePedido(pedidos.listarParaCocina(), pedido.getId()));
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero");
        assertFalse(contienePedido(pedidos.listarParaCocina(), pedido.getId()));
    }

    @Test
    void flujoCompletoAmericanBitesRegistraHistorialPagaCierraYPermiteReabrir() {
        Ingrediente carne = crearIngrediente("Carne de res", true);
        Ingrediente pan = crearIngrediente("Pan brioche", true);
        Plato combo = crearPlato("Combo American", "28.00", true, carne, pan);
        Pedido pedido = pedidos.crear(cuenta.getId());
        pedido = pedidos.agregarItem(pedido.getId(), combo.getId(), 1);
        pedido = pedidos.confirmar(pedido.getId());
        Long pedidoId = pedido.getId();
        Long itemId = pedido.getItems().getFirst().getId();

        assertTrue(contienePedido(pedidos.listarParaCocina(), pedidoId));

        pedidos.cambiarEstado(pedidoId, EstadoPedido.EN_PREPARACION, "cocinero-1");
        pedidos.cambiarEstado(pedidoId, EstadoPedido.LISTO, "cocinero-2");
        pedidos.cambiarEstado(pedidoId, EstadoPedido.ENTREGADO, "mesero-1");

        List<CambioEstadoPedido> historial = pedidos.obtenerHistorial(pedidoId);
        assertEquals(3, historial.size());
        validarCambio(historial.get(0), EstadoPedido.RECIBIDO,
                EstadoPedido.EN_PREPARACION, "cocinero-1");
        validarCambio(historial.get(1), EstadoPedido.EN_PREPARACION,
                EstadoPedido.LISTO, "cocinero-2");
        validarCambio(historial.get(2), EstadoPedido.LISTO,
                EstadoPedido.ENTREGADO, "mesero-1");

        Pago pago = pagos.registrarPago(cuenta.getId());
        Cuenta cuentaCerrada = cuentas.obtenerPorId(cuenta.getId());
        assertAll(
                () -> assertEquals(new BigDecimal("28.00"), pago.getMonto()),
                () -> assertEquals(EstadoCuenta.CERRADA, cuentaCerrada.getEstado()),
                () -> assertNotNull(cuentaCerrada.getFechaCierre()),
                () -> assertThrows(BusinessRuleException.class,
                        () -> pedidos.actualizarCantidadItem(
                                pedidoId, itemId, 2)));

        Cuenta nuevaCuenta = cuentas.abrirCuenta(cuenta.getMesaId());
        assertAll(
                () -> assertNotEquals(cuenta.getId(), nuevaCuenta.getId()),
                () -> assertEquals(EstadoCuenta.ABIERTA, nuevaCuenta.getEstado()),
                () -> assertTrue(nuevaCuenta.getPedidos().isEmpty()));
    }

    private Ingrediente crearIngrediente(String nombre, boolean disponible) {
        return ingredientes.crear(Ingrediente.builder()
                .nombre(nombre)
                .disponible(disponible)
                .build());
    }

    private Plato crearPlato(String nombre, String precio, boolean combo,
            Ingrediente... ingredientesDelPlato) {
        return platos.crear(Plato.builder()
                        .nombre(nombre)
                        .descripcion(nombre)
                        .precio(new BigDecimal(precio))
                        .combo(combo)
                        .build(),
                java.util.Arrays.stream(ingredientesDelPlato)
                        .map(Ingrediente::getId)
                        .toList());
    }

    private void completarFlujoCocina(Pedido pedido) {
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.EN_PREPARACION, "cocinero");
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.LISTO, "cocinero");
        pedidos.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO, "mesero");
    }

    private boolean contienePedido(List<Pedido> lista, Long pedidoId) {
        return lista.stream().anyMatch(actual -> actual.getId().equals(pedidoId));
    }

    private void validarCambio(CambioEstadoPedido cambio, EstadoPedido anterior,
            EstadoPedido nuevo, String usuario) {
        assertAll(
                () -> assertEquals(anterior, cambio.getEstadoAnterior()),
                () -> assertEquals(nuevo, cambio.getEstadoNuevo()),
                () -> assertEquals(usuario, cambio.getUsuarioResponsable()),
                () -> assertNotNull(cambio.getFechaHora()));
    }
}
