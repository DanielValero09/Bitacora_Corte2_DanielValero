package com.restaurante.service.auditoria;

import com.mongodb.MongoException;
import com.restaurante.mapper.EventoRestauranteMapper;
import com.restaurante.model.document.EventoRestauranteDocument;
import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.repository.EventoRestauranteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Verifica sincronización Spring real con un gestor sin BD, no persistencia real. */
@ExtendWith(OutputCaptureExtension.class)
class EventoAuditoriaMongoListenerTest {
    private AnnotationConfigApplicationContext context;
    private EventoRestauranteRepository repository;
    private GestorTransaccionesDePrueba manager;
    private TransactionTemplate transaction;
    private final LocalDateTime fecha = LocalDateTime.of(2026, 10, 6, 10, 30);

    @BeforeEach
    void iniciar() {
        repository = mock(EventoRestauranteRepository.class);
        manager = new GestorTransaccionesDePrueba();
        transaction = new TransactionTemplate(manager);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(EventoRestauranteRepository.class, () -> repository);
        context.registerBean(EventoRestauranteMapper.class,
                () -> Mappers.getMapper(EventoRestauranteMapper.class));
        context.registerBean(EventoAuditoriaMongoListener.class);
        context.refresh();
    }

    @AfterEach
    void cerrar() {
        context.close();
    }

    @ParameterizedTest
    @MethodSource("transiciones")
    void guardaEventoCompletoSoloDespuesDeCommit(EstadoPedido anterior, EstadoPedido nuevo) {
        when(repository.save(any())).thenAnswer(invocation -> {
            assertEquals(1, manager.commits);
            return invocation.getArgument(0);
        });
        transaction.executeWithoutResult(status -> {
            context.publishEvent(new CambioEstadoPedidoAuditEvent(7L, anterior, nuevo, "cocinero", fecha));
            verifyNoInteractions(repository);
        });

        ArgumentCaptor<EventoRestauranteDocument> captor =
                ArgumentCaptor.forClass(EventoRestauranteDocument.class);
        verify(repository).save(captor.capture());
        verifyNoMoreInteractions(repository);
        var document = captor.getValue();
        assertNull(document.getId());
        assertEquals("CAMBIO_ESTADO_PEDIDO", document.getTipo());
        assertEquals("Pedido", document.getEntidadTipo());
        assertEquals(7L, document.getEntidadId());
        assertEquals("Pedido cambió de " + anterior + " a " + nuevo, document.getDescripcion());
        assertEquals("cocinero", document.getUsuario());
        assertEquals(fecha, document.getTimestamp());
        assertEquals(Map.of("estadoAnterior", anterior.name(), "estadoNuevo", nuevo.name()),
                document.getMetadatos());
    }

    @Test
    void rollbackNoGuardaAuditoria() {
        transaction.executeWithoutResult(status -> {
            context.publishEvent(evento());
            status.setRollbackOnly();
        });

        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        verifyNoInteractions(repository);
    }

    @Test
    void excepcionDeOperacionPrincipalNoGuardaAuditoria() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            context.publishEvent(evento());
            throw new IllegalStateException("operación principal fallida");
        }));

        assertEquals(1, manager.rollbacks);
        verifyNoInteractions(repository);
    }

    @Test
    void falloAlConfirmarTransaccionNoGuardaAuditoria() {
        manager.fallarCommit = true;

        assertThrows(TransactionSystemException.class,
                () -> transaction.executeWithoutResult(status -> context.publishEvent(evento())));

        assertEquals(0, manager.commits);
        verifyNoInteractions(repository);
    }

    @Test
    void publicacionSinTransaccionNoActivaFallback() {
        context.publishEvent(evento());

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("fallosMongo")
    void falloMongoConservaCommitYRespuestaSinExponerSecretos(
            RuntimeException fallo, CapturedOutput output) {
        when(repository.save(any())).thenThrow(fallo);

        String respuesta = assertDoesNotThrow(() -> transaction.execute(status -> {
            context.publishEvent(evento());
            return "pedido actualizado";
        }));

        assertEquals("pedido actualizado", respuesta);
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
        verify(repository).save(any(EventoRestauranteDocument.class));
        assertTrue(output.getAll().contains("tipo=CAMBIO_ESTADO_PEDIDO, pedidoId=7"));
        assertFalse(output.getAll().contains("material-sensible-de-prueba"));
    }

    private CambioEstadoPedidoAuditEvent evento() {
        return new CambioEstadoPedidoAuditEvent(7L, EstadoPedido.RECIBIDO,
                EstadoPedido.EN_PREPARACION, "cocinero", fecha);
    }

    private static Stream<Arguments> transiciones() {
        return Stream.of(
                Arguments.of(EstadoPedido.RECIBIDO, EstadoPedido.EN_PREPARACION),
                Arguments.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO),
                Arguments.of(EstadoPedido.LISTO, EstadoPedido.ENTREGADO));
    }

    private static Stream<RuntimeException> fallosMongo() {
        return Stream.of(new DataAccessResourceFailureException("material-sensible-de-prueba"),
                new MongoException("material-sensible-de-prueba"));
    }

    private static class GestorTransaccionesDePrueba extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;
        private boolean fallarCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (fallarCommit) {
                throw new TransactionSystemException("commit rechazado");
            }
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
