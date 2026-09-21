package com.restaurante.mapper;

import com.restaurante.model.domain.Ingrediente;
import com.restaurante.model.domain.Plato;
import com.restaurante.model.dto.response.PlatoResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlatoMapperTest {
    @Test
    void responseConsultaDisponibilidadActualDelDominioYMapeaIngredientes() {
        PlatoMapper mapper = Mappers.getMapper(PlatoMapper.class);
        ReflectionTestUtils.setField(mapper, "ingredienteMapper", Mappers.getMapper(IngredienteMapper.class));
        Ingrediente queso = Ingrediente.builder().id(1L).nombre("Queso").disponible(true).build();
        Plato plato = Plato.builder().id(2L).nombre("Hamburguesa").activo(true)
                .ingredientes(List.of(queso)).build();

        PlatoResponse disponible = mapper.toResponse(plato);
        assertTrue(disponible.disponible());
        assertEquals(queso.getId(), disponible.ingredientes().getFirst().id());
        assertEquals("Queso", disponible.ingredientes().getFirst().nombre());

        queso.setDisponible(false);
        PlatoResponse agotado = mapper.toResponse(plato);
        assertEquals(plato.isDisponible(), agotado.disponible());
        assertFalse(agotado.disponible());
        assertFalse(agotado.ingredientes().getFirst().disponible());

        queso.setDisponible(true);
        plato.setActivo(false);
        assertFalse(mapper.toResponse(plato).disponible());
    }
}
