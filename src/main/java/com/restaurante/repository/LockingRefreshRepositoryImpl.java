package com.restaurante.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

public class LockingRefreshRepositoryImpl implements LockingRefreshRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void refreshForUpdate(Object entity) {
        // Conserva cambios de llamadas anteriores dentro de la misma transaccion.
        // El servicio ya posee el lock de Cuenta antes de refrescar sus pedidos.
        entityManager.flush();
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    }
}
