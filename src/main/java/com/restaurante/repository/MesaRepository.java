package com.restaurante.repository;

import com.restaurante.model.entity.MesaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<MesaEntity, Long> {

    boolean existsByNumero(Integer numero);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MesaEntity m where m.id = :id")
    Optional<MesaEntity> findByIdForUpdate(@Param("id") Long id);
}
