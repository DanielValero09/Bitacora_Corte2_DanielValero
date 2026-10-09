package com.restaurante.service.auditoria;

import com.restaurante.mapper.EventoRestauranteMapper;
import com.restaurante.model.domain.EventoRestaurante;
import com.restaurante.repository.EventoRestauranteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Auditoría best-effort after commit: PostgreSQL ya confirmó el historial RN-07. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventoAuditoriaMongoListener {
    private final EventoRestauranteMapper mapper;
    private final EventoRestauranteRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void auditarCambioEstado(CambioEstadoPedidoAuditEvent cambio) {
        try {
            EventoRestaurante evento = EventoRestaurante.builder()
                    .tipo("CAMBIO_ESTADO_PEDIDO")
                    .entidadTipo("Pedido")
                    .entidadId(cambio.pedidoId())
                    .descripcion("Pedido cambió de " + cambio.estadoAnterior()
                            + " a " + cambio.estadoNuevo())
                    .usuario(cambio.usuarioResponsable())
                    .timestamp(cambio.fechaHora())
                    .metadatos(Map.of(
                            "estadoAnterior", cambio.estadoAnterior().name(),
                            "estadoNuevo", cambio.estadoNuevo().name()))
                    .build();
            repository.save(mapper.toDocument(evento));
        } catch (RuntimeException ex) {
            // No incluir mensaje/stack trace: el driver podría exponer la URI o credenciales.
            log.error("No se pudo persistir auditoría Mongo: tipo=CAMBIO_ESTADO_PEDIDO, pedidoId={}",
                    cambio.pedidoId());
        }
    }
}
