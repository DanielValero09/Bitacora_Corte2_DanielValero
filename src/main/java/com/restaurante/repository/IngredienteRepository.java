package com.restaurante.repository;

import com.restaurante.model.entity.IngredienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredienteRepository extends JpaRepository<IngredienteEntity, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
}
