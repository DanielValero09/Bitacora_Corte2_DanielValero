package com.restaurante.support;

import com.restaurante.mapper.IngredienteEntityMapper;
import com.restaurante.mapper.PlatoEntityMapper;
import com.restaurante.model.entity.IngredienteEntity;
import com.restaurante.model.entity.PlatoEntity;
import com.restaurante.repository.IngredienteRepository;
import com.restaurante.repository.PlatoRepository;
import com.restaurante.service.impl.IngredienteServiceImpl;
import com.restaurante.service.impl.PlatoServiceImpl;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Infraestructura de prueba para los tests que integran el catalogo con modulos
 * que todavia permanecen en memoria. Los mapas pertenecen exclusivamente al
 * doble Mockito de los repositories; no forman parte de los services productivos.
 */
public final class CatalogoTestFixture {
    private final Map<Long, IngredienteEntity> ingredientes = new LinkedHashMap<>();
    private final Map<Long, PlatoEntity> platos = new LinkedHashMap<>();
    private final AtomicLong secuenciaIngredientes = new AtomicLong();
    private final AtomicLong secuenciaPlatos = new AtomicLong();
    private final IngredienteServiceImpl ingredienteService;
    private final PlatoServiceImpl platoService;

    public CatalogoTestFixture() {
        IngredienteRepository ingredienteRepository = mock(IngredienteRepository.class);
        PlatoRepository platoRepository = mock(PlatoRepository.class);
        configurarIngredienteRepository(ingredienteRepository);
        configurarPlatoRepository(platoRepository);

        IngredienteEntityMapper ingredienteMapper = Mappers.getMapper(IngredienteEntityMapper.class);
        PlatoEntityMapper platoMapper = Mappers.getMapper(PlatoEntityMapper.class);
        ReflectionTestUtils.setField(platoMapper, "ingredienteEntityMapper", ingredienteMapper);

        ingredienteService = new IngredienteServiceImpl(ingredienteRepository, ingredienteMapper);
        platoService = new PlatoServiceImpl(platoRepository, ingredienteRepository, platoMapper);
    }

    public IngredienteServiceImpl ingredientes() {
        return ingredienteService;
    }

    public PlatoServiceImpl platos() {
        return platoService;
    }

    private void configurarIngredienteRepository(IngredienteRepository repository) {
        when(repository.existsByNombreIgnoreCase(anyString())).thenAnswer(invocation -> {
            String nombre = invocation.getArgument(0);
            return ingredientes.values().stream()
                    .anyMatch(entity -> entity.getNombre().equalsIgnoreCase(nombre));
        });
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(ingredientes.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> ingredientes.values().stream()
                .sorted(Comparator.comparing(IngredienteEntity::getId))
                .toList());
        when(repository.save(any(IngredienteEntity.class))).thenAnswer(invocation -> {
            IngredienteEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(secuenciaIngredientes.incrementAndGet());
            }
            ingredientes.put(entity.getId(), entity);
            return entity;
        });
    }

    private void configurarPlatoRepository(PlatoRepository repository) {
        when(repository.existsByNombreIgnoreCase(anyString())).thenAnswer(invocation -> {
            String nombre = invocation.getArgument(0);
            return platos.values().stream()
                    .anyMatch(entity -> entity.getNombre().equalsIgnoreCase(nombre));
        });
        when(repository.existsByNombreIgnoreCaseAndIdNot(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    String nombre = invocation.getArgument(0);
                    Long id = invocation.getArgument(1);
                    return platos.values().stream()
                            .anyMatch(entity -> !entity.getId().equals(id)
                                    && entity.getNombre().equalsIgnoreCase(nombre));
                });
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(platos.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> platos.values().stream()
                .sorted(Comparator.comparing(PlatoEntity::getId))
                .toList());
        when(repository.findByActivoTrue()).thenAnswer(invocation -> platos.values().stream()
                .filter(PlatoEntity::isActivo)
                .toList());
        when(repository.save(any(PlatoEntity.class))).thenAnswer(invocation -> {
            PlatoEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(secuenciaPlatos.incrementAndGet());
            }
            platos.put(entity.getId(), entity);
            return entity;
        });
    }
}
