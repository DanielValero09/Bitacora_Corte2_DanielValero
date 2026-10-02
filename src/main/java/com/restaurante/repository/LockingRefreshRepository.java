package com.restaurante.repository;

/** Operaciones sobre entidades administradas en el contexto de la transaccion actual. */
public interface LockingRefreshRepository {

    /** Relee la entidad bajo un lock de escritura, incluso si ya estaba precargada. */
    void refreshForUpdate(Object entity);
}
