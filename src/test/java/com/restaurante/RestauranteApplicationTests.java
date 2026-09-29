package com.restaurante;

import com.restaurante.repository.IngredienteRepository;
import com.restaurante.repository.PlatoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")
class RestauranteApplicationTests {
    @MockitoBean
    private IngredienteRepository ingredienteRepository;

    @MockitoBean
    private PlatoRepository platoRepository;

    @Test
    void contextLoads() {
    }
}
