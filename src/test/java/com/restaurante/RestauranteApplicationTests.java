package com.restaurante;

import com.restaurante.repository.IngredienteRepository;
import com.restaurante.repository.CuentaRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PagoRepository;
import com.restaurante.repository.PedidoRepository;
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
    private com.restaurante.repository.UsuarioRepository usuarioRepository;
    @MockitoBean
    private IngredienteRepository ingredienteRepository;

    @MockitoBean
    private PlatoRepository platoRepository;

    @MockitoBean
    private MesaRepository mesaRepository;

    @MockitoBean
    private CuentaRepository cuentaRepository;

    @MockitoBean
    private PedidoRepository pedidoRepository;

    @MockitoBean
    private PagoRepository pagoRepository;

    @Test
    void contextLoads() {
    }
}
