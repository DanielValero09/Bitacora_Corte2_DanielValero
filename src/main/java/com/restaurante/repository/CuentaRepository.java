package com.restaurante.repository;

import com.restaurante.model.domain.enums.EstadoCuenta;
import com.restaurante.model.entity.CuentaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CuentaRepository extends JpaRepository<CuentaEntity, Long>, LockingRefreshRepository {

    Optional<CuentaEntity> findByMesaIdAndEstado(Long mesaId, EstadoCuenta estado);

    boolean existsByMesaIdAndEstado(Long mesaId, EstadoCuenta estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaEntity c where c.id = :id")
    Optional<CuentaEntity> findByIdForUpdate(@Param("id") Long id);
}
