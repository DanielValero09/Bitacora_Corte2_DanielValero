package com.restaurante.repository;

import com.restaurante.model.domain.enums.EstadoPedido;
import com.restaurante.model.entity.PedidoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<PedidoEntity, Long>, LockingRefreshRepository {

    List<PedidoEntity> findByCuentaIdOrderByIdAsc(Long cuentaId);

    List<PedidoEntity> findByConfirmadoTrueAndEstadoNotOrderByIdAsc(EstadoPedido estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PedidoEntity p where p.id = :id")
    Optional<PedidoEntity> findByIdForUpdate(@Param("id") Long id);
}
