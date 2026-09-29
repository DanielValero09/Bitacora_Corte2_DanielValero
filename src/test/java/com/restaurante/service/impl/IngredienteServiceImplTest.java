package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.IngredienteEntityMapper;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.entity.IngredienteEntity;
import com.restaurante.repository.IngredienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngredienteServiceImplTest {
    @Mock
    private IngredienteRepository repository;
    @Mock
    private IngredienteEntityMapper mapper;
    @InjectMocks
    private IngredienteServiceImpl service;

    private final Map<Long, IngredienteEntity> almacen = new LinkedHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @BeforeEach
    void setUp() {
        when(mapper.toEntity(any(Ingrediente.class))).thenAnswer(invocation -> {
            Ingrediente ingrediente = invocation.getArgument(0);
            return IngredienteEntity.builder()
                    .id(ingrediente.getId())
                    .nombre(ingrediente.getNombre())
                    .disponible(ingrediente.isDisponible())
                    .build();
        });
        when(mapper.toDomain(any(IngredienteEntity.class))).thenAnswer(invocation -> {
            IngredienteEntity entity = invocation.getArgument(0);
            return Ingrediente.builder()
                    .id(entity.getId())
                    .nombre(entity.getNombre())
                    .disponible(entity.isDisponible())
                    .build();
        });
        when(repository.existsByNombreIgnoreCase(anyString())).thenAnswer(invocation -> {
            String nombre = invocation.getArgument(0);
            return almacen.values().stream()
                    .anyMatch(entity -> entity.getNombre().equalsIgnoreCase(nombre));
        });
        when(repository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(almacen.get(invocation.<Long>getArgument(0))));
        when(repository.findAll(any(Sort.class))).thenAnswer(invocation -> List.copyOf(almacen.values()));
        when(repository.save(any(IngredienteEntity.class))).thenAnswer(invocation -> {
            IngredienteEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(secuencia.incrementAndGet());
            }
            almacen.put(entity.getId(), entity);
            return entity;
        });
    }

    @Test
    void crearUsaIdGeneradoPorRepositoryYConservaDatos() {
        Ingrediente ingrediente = nuevo(" Queso ");
        ingrediente.setId(999L);

        Ingrediente creado = service.crear(ingrediente);

        assertEquals(1L, creado.getId());
        assertEquals("Queso", creado.getNombre());
        assertTrue(creado.isDisponible());
        assertEquals(creado.getId(), service.obtenerPorId(creado.getId()).getId());
    }

    @Test
    void idsProvienenDelResultadoDeSave() {
        assertEquals(1L, service.crear(nuevo("Queso")).getId());
        assertEquals(2L, service.crear(nuevo("Pan")).getId());
        verify(repository, times(2)).save(any(IngredienteEntity.class));
    }

    @Test
    void rechazaDuplicadoIgnorandoMayusculasYEspacios() {
        service.crear(nuevo("Queso"));

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.crear(nuevo(" queso ")));
        assertEquals(1, service.listar().size());
    }

    @Test
    void obtieneIngredienteExistente() {
        Ingrediente creado = service.crear(nuevo("Pan"));

        Ingrediente obtenido = service.obtenerPorId(creado.getId());

        assertEquals(creado.getId(), obtenido.getId());
        assertEquals("Pan", obtenido.getNombre());
    }

    @Test
    void obtenerInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarInicialmenteVacio() {
        assertTrue(service.listar().isEmpty());
    }

    @Test
    void listarContieneTodosEnOrdenDeId() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        Ingrediente pan = service.crear(nuevo("Pan"));

        assertEquals(List.of(queso.getId(), pan.getId()),
                service.listar().stream().map(Ingrediente::getId).toList());
    }

    @Test
    void cambiarDisponibilidadPersisteEnAmbosSentidos() {
        Ingrediente creado = service.crear(nuevo("Queso"));

        assertFalse(service.cambiarDisponibilidad(creado.getId(), false).isDisponible());
        assertTrue(service.cambiarDisponibilidad(creado.getId(), true).isDisponible());
        verify(repository, times(3)).save(any(IngredienteEntity.class));
    }

    @Test
    void cambiarDisponibilidadInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.cambiarDisponibilidad(99L, false));
        verify(repository, never()).save(any(IngredienteEntity.class));
    }

    @Test
    void obtenerPorIdsMantieneOrden() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        Ingrediente pan = service.crear(nuevo("Pan"));

        List<Ingrediente> resultado = service.obtenerPorIds(List.of(pan.getId(), queso.getId()));

        assertEquals(List.of(pan.getId(), queso.getId()),
                resultado.stream().map(Ingrediente::getId).toList());
    }

    @Test
    void obtenerPorIdsConInexistenteLanzaExcepcion() {
        Ingrediente queso = service.crear(nuevo("Queso"));
        assertThrows(ResourceNotFoundException.class,
                () -> service.obtenerPorIds(List.of(queso.getId(), 99L)));
    }

    @Test
    void obtenerPorIdsVacioPermitePlatosSinIngredientes() {
        assertTrue(service.obtenerPorIds(List.of()).isEmpty());
    }

    private Ingrediente nuevo(String nombre) {
        return Ingrediente.builder().nombre(nombre).disponible(true).build();
    }
}
