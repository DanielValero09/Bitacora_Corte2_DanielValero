package com.restaurante.service.impl;

import com.restaurante.exception.ResourceAlreadyExistsException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.mapper.PlatoEntityMapper;
import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.entity.IngredienteEntity;
import com.restaurante.model.entity.PlatoEntity;
import com.restaurante.repository.IngredienteRepository;
import com.restaurante.repository.PlatoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatoServiceImplTest {
    @Mock
    private PlatoRepository platoRepository;
    @Mock
    private IngredienteRepository ingredienteRepository;
    @Mock
    private PlatoEntityMapper mapper;
    @InjectMocks
    private PlatoServiceImpl service;

    private final Map<Long, PlatoEntity> platos = new LinkedHashMap<>();
    private final Map<Long, IngredienteEntity> ingredientes = new LinkedHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    @BeforeEach
    void setUp() {
        configurarMapper();
        configurarRepositories();
    }

    @Test
    void crearUsaIdGeneradoPorRepositoryYConservaCamposEditables() {
        Plato entrada = nuevo(" Hamburguesa ");
        entrada.setId(999L);
        entrada.setCombo(true);

        Plato creado = service.crear(entrada, List.of());

        assertEquals(1L, creado.getId());
        assertEquals("Hamburguesa", creado.getNombre());
        assertEquals("Descripcion", creado.getDescripcion());
        assertEquals(new BigDecimal("15000"), creado.getPrecio());
        assertTrue(creado.isCombo());
    }

    @Test
    void crearSiempreIniciaActivo() {
        Plato entrada = nuevo("Hamburguesa");
        entrada.setActivo(false);
        assertTrue(service.crear(entrada, List.of()).isActivo());
    }

    @Test
    void crearResuelveIngredientesPersistidos() {
        guardarIngrediente(7L, "Queso", true);

        Plato plato = service.crear(nuevo("Hamburguesa"), List.of(7L));

        assertEquals(7L, plato.getIngredientes().getFirst().getId());
        verify(ingredienteRepository).findById(7L);
    }

    @Test
    void ingredienteInexistenteSePropagaSinGuardarPlato() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.crear(nuevo("Hamburguesa"), List.of(99L)));
        assertTrue(service.listarTodos().isEmpty());
        verify(platoRepository, never()).save(any(PlatoEntity.class));
    }

    @Test
    void rechazaNombreDuplicadoIgnorandoMayusculasYEspacios() {
        service.crear(nuevo("Hamburguesa"), List.of());
        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.crear(nuevo(" hamburguesa "), List.of()));
    }

    @Test
    void obtenerExistente() {
        Plato creado = service.crear(nuevo("Hamburguesa"), List.of());
        assertEquals(creado.getId(), service.obtenerPorId(creado.getId()).getId());
    }

    @Test
    void obtenerInexistente() {
        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPorId(99L));
    }

    @Test
    void listarTodosVacio() {
        assertTrue(service.listarTodos().isEmpty());
    }

    @Test
    void listarTodosIncluyeActivosEInactivosConIdsDistintos() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(primero.getId());

        assertNotEquals(primero.getId(), segundo.getId());
        assertEquals(List.of(primero.getId(), segundo.getId()),
                service.listarTodos().stream().map(Plato::getId).toList());
        assertFalse(service.listarTodos().getFirst().isActivo());
    }

    @Test
    void cartaExcluyeInactivos() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(primero.getId());

        assertEquals(List.of(segundo.getId()),
                service.listarCarta().stream().map(Plato::getId).toList());
    }

    @Test
    void cartaIncluyeActivoNoDisponible() {
        guardarIngrediente(7L, "Queso", false);
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of(7L));

        assertFalse(plato.isDisponible());
        assertEquals(List.of(plato.getId()),
                service.listarCarta().stream().map(Plato::getId).toList());
    }

    @Test
    void actualizarReemplazaCamposEditablesEIngredientes() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        guardarIngrediente(7L, "Queso", false);
        Plato cambios = nuevo(" Hamburguesa especial ");
        cambios.setDescripcion("");
        cambios.setPrecio(new BigDecimal("20000"));
        cambios.setCombo(true);

        Plato actualizado = service.actualizar(original.getId(), cambios, List.of(7L));

        assertEquals(original.getId(), actualizado.getId());
        assertEquals("Hamburguesa especial", actualizado.getNombre());
        assertEquals("", actualizado.getDescripcion());
        assertEquals(new BigDecimal("20000"), actualizado.getPrecio());
        assertTrue(actualizado.isCombo());
        assertEquals(7L, actualizado.getIngredientes().getFirst().getId());
        assertFalse(actualizado.isDisponible());
    }

    @Test
    void actualizarConservaIdIgnorandoIdDeCambios() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        Plato cambios = nuevo("Papas");
        cambios.setId(999L);
        assertEquals(original.getId(),
                service.actualizar(original.getId(), cambios, List.of()).getId());
    }

    @Test
    void actualizarConservaActivoTrue() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        assertTrue(service.actualizar(original.getId(), nuevo("Papas"), List.of()).isActivo());
    }

    @Test
    void actualizarConservaActivoFalse() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());
        service.desactivar(original.getId());
        Plato cambios = nuevo("Papas");
        cambios.setActivo(true);
        assertFalse(service.actualizar(original.getId(), cambios, List.of()).isActivo());
    }

    @Test
    void actualizarRechazaNombreDeOtroPlatoInclusoInactivo() {
        Plato primero = service.crear(nuevo("Hamburguesa"), List.of());
        Plato segundo = service.crear(nuevo("Papas"), List.of());
        service.desactivar(segundo.getId());

        assertThrows(ResourceAlreadyExistsException.class,
                () -> service.actualizar(primero.getId(), nuevo(" PAPAS "), List.of()));
        assertEquals("Hamburguesa", service.obtenerPorId(primero.getId()).getNombre());
    }

    @Test
    void actualizarPermiteConservarSuPropioNombre() {
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of());
        assertEquals("HAMBURGUESA",
                service.actualizar(plato.getId(), nuevo(" HAMBURGUESA "), List.of()).getNombre());
    }

    @Test
    void actualizarInexistenteFallaAntesDeResolverIngredientes() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizar(99L, nuevo("Papas"), List.of(7L)));
        verify(ingredienteRepository, never()).findById(anyLong());
    }

    @Test
    void actualizarConIngredienteInexistenteNoDejaCambiosParciales() {
        Plato original = service.crear(nuevo("Hamburguesa"), List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizar(original.getId(), nuevo("Papas"), List.of(99L)));
        assertEquals("Hamburguesa", service.obtenerPorId(original.getId()).getNombre());
        assertTrue(service.obtenerPorId(original.getId()).getIngredientes().isEmpty());
    }

    @Test
    void desactivarEsLogicoEIdempotente() {
        Plato plato = service.crear(nuevo("Hamburguesa"), List.of());
        service.desactivar(plato.getId());
        service.desactivar(plato.getId());

        assertFalse(service.obtenerPorId(plato.getId()).isActivo());
        assertEquals(List.of(plato.getId()),
                service.listarTodos().stream().map(Plato::getId).toList());
        verify(platoRepository, never()).deleteById(anyLong());
    }

    @Test
    void desactivarInexistenteLanzaExcepcion() {
        assertThrows(ResourceNotFoundException.class, () -> service.desactivar(99L));
    }

    private void configurarMapper() {
        when(mapper.toEntity(any(Plato.class))).thenAnswer(invocation -> {
            Plato plato = invocation.getArgument(0);
            return PlatoEntity.builder()
                    .id(plato.getId())
                    .nombre(plato.getNombre())
                    .descripcion(plato.getDescripcion())
                    .precio(plato.getPrecio())
                    .activo(plato.isActivo())
                    .combo(plato.isCombo())
                    .ingredientes(new ArrayList<>())
                    .build();
        });
        when(mapper.toDomain(any(PlatoEntity.class))).thenAnswer(invocation -> {
            PlatoEntity entity = invocation.getArgument(0);
            List<Ingrediente> ingredientesDomain = entity.getIngredientes().stream()
                    .map(ingrediente -> Ingrediente.builder()
                            .id(ingrediente.getId())
                            .nombre(ingrediente.getNombre())
                            .disponible(ingrediente.isDisponible())
                            .build())
                    .toList();
            return Plato.builder()
                    .id(entity.getId())
                    .nombre(entity.getNombre())
                    .descripcion(entity.getDescripcion())
                    .precio(entity.getPrecio())
                    .activo(entity.isActivo())
                    .combo(entity.isCombo())
                    .ingredientes(ingredientesDomain)
                    .build();
        });
    }

    private void configurarRepositories() {
        when(platoRepository.existsByNombreIgnoreCase(anyString())).thenAnswer(invocation -> {
            String nombre = invocation.getArgument(0);
            return platos.values().stream()
                    .anyMatch(entity -> entity.getNombre().equalsIgnoreCase(nombre));
        });
        when(platoRepository.existsByNombreIgnoreCaseAndIdNot(anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    String nombre = invocation.getArgument(0);
                    Long id = invocation.getArgument(1);
                    return platos.values().stream()
                            .anyMatch(entity -> !entity.getId().equals(id)
                                    && entity.getNombre().equalsIgnoreCase(nombre));
                });
        when(platoRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(platos.get(invocation.<Long>getArgument(0))));
        when(platoRepository.findAll(any(Sort.class))).thenAnswer(invocation -> List.copyOf(platos.values()));
        when(platoRepository.findByActivoTrue()).thenAnswer(invocation -> platos.values().stream()
                .filter(PlatoEntity::isActivo)
                .toList());
        when(platoRepository.save(any(PlatoEntity.class))).thenAnswer(invocation -> {
            PlatoEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(secuencia.incrementAndGet());
            }
            platos.put(entity.getId(), entity);
            return entity;
        });
        when(ingredienteRepository.findById(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(ingredientes.get(invocation.<Long>getArgument(0))));
    }

    private void guardarIngrediente(Long id, String nombre, boolean disponible) {
        ingredientes.put(id, IngredienteEntity.builder()
                .id(id)
                .nombre(nombre)
                .disponible(disponible)
                .build());
    }

    private Plato nuevo(String nombre) {
        return Plato.builder()
                .nombre(nombre)
                .descripcion("Descripcion")
                .precio(new BigDecimal("15000"))
                .build();
    }
}
