package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.MesaEntityMapper;
import com.restaurante.model.domain.Mesa;
import com.restaurante.model.entity.MesaEntity;
import com.restaurante.repository.MesaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MesaServiceImplTest {
    @Mock
    private MesaRepository repository;
    @Mock
    private MesaEntityMapper mapper;
    private MesaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MesaServiceImpl(repository, mapper);
    }

    @Test
    void crearDelegaIdALaBaseDeDatos() {
        Mesa entrada = Mesa.builder().id(99L).numero(10).build();
        MesaEntity entity = MesaEntity.builder().id(99L).numero(10).build();
        MesaEntity guardada = MesaEntity.builder().id(1L).numero(10).build();
        Mesa salida = Mesa.builder().id(1L).numero(10).build();
        when(mapper.toEntity(entrada)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(guardada);
        when(mapper.toDomain(guardada)).thenReturn(salida);

        Mesa creada = service.crear(entrada);

        assertEquals(1L, creada.getId());
        assertEquals(10, creada.getNumero());
        assertEquals(null, entity.getId());
    }

    @Test
    void numeroDuplicadoLanzaExcepcionSinGuardar() {
        when(repository.existsByNumero(10)).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.crear(Mesa.builder().numero(10).build()));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void restriccionUnicaConcurrenteSeTraduceAConflicto() {
        Mesa entrada = Mesa.builder().numero(10).build();
        MesaEntity entity = MesaEntity.builder().numero(10).build();
        when(mapper.toEntity(entrada)).thenReturn(entity);
        when(repository.saveAndFlush(entity))
                .thenThrow(new DataIntegrityViolationException("uk_mesas_numero"));

        assertThrows(ResourceAlreadyExistsException.class, () -> service.crear(entrada));
    }

    @Test
    void obtenerMesaExistente() {
        MesaEntity entity = MesaEntity.builder().id(1L).numero(10).build();
        Mesa domain = Mesa.builder().id(1L).numero(10).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertEquals(domain, service.obtenerPorId(1L));
    }

    @Test
    void mesaInexistenteLanzaExcepcion() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of());
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarMapeaOrdenSolicitadoAlRepository() {
        MesaEntity entity = MesaEntity.builder().id(1L).numero(20).build();
        Mesa domain = Mesa.builder().id(1L).numero(20).build();
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertEquals(List.of(domain), service.listar());
        verify(repository).findAll(Sort.by(Sort.Direction.ASC, "id"));
    }
}
