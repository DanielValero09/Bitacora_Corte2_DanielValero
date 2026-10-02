package com.restaurante.repository;

import com.restaurante.model.entity.PagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<PagoEntity, Long> {

    Optional<PagoEntity> findByCuentaId(Long cuentaId);

    boolean existsByCuentaId(Long cuentaId);
}
