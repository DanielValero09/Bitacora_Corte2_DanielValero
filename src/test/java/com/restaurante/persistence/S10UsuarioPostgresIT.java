package com.restaurante.persistence;

import com.restaurante.RestauranteApplication;
import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.repository.UsuarioRepository;
import com.restaurante.security.AdminBootstrap;
import com.restaurante.security.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: BD dedicada, conserva filas y usa update. Ninguna BD embebida. */
@EnabledIfSystemProperty(named = "s10.postgres", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class S10UsuarioPostgresIT {
    @Test
    void gerenteBcryptPersisteTrasReinicioYDesactivarRevocaAutenticacion() {
        String email = "s10-" + UUID.randomUUID() + "@example.test";
        String password = "password-exclusivo-test-" + UUID.randomUUID();
        Long id;
        try (var context = arrancar()) {
            var repository = context.getBean(UsuarioRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            var bootstrap = new AdminBootstrap(repository, encoder, email, password);
            bootstrap.run(null);
            bootstrap.run(null);
            var usuario = repository.findByEmailIgnoreCase(email.toUpperCase()).orElseThrow();
            id = usuario.getId();
            assertEquals(RolUsuario.ROLE_GERENTE, usuario.getRol());
            assertTrue(usuario.isActivo());
            assertTrue(encoder.matches(password, usuario.getPassword()));
            String stored = context.getBean(JdbcTemplate.class).queryForObject(
                    "select password from usuarios where id = ?", String.class, id);
            assertNotEquals(password, stored);
            assertTrue(stored.startsWith("$2a$"));
            assertEquals(1L, context.getBean(JdbcTemplate.class).queryForObject(
                    "select count(*) from usuarios where email = ?", Long.class, email));
        }
        try (var context = arrancar()) {
            var repository = context.getBean(UsuarioRepository.class);
            var usuario = repository.findByEmailIgnoreCase(email).orElseThrow();
            assertEquals(id, usuario.getId());
            assertTrue(context.getBean(PasswordEncoder.class).matches(password, usuario.getPassword()));
            assertEquals("ROLE_GERENTE", context.getBean(UsuarioDetailsService.class).loadUserByUsername(email)
                    .getAuthorities().iterator().next().getAuthority());
            usuario.setActivo(false);
            repository.saveAndFlush(usuario);
            assertThrows(UsernameNotFoundException.class, () ->
                    context.getBean(UsuarioDetailsService.class).loadUserByUsername(email));
        }
    }

    private ConfigurableApplicationContext arrancar() {
        return new SpringApplicationBuilder(RestauranteApplication.class).web(WebApplicationType.NONE)
                .run("--spring.jpa.hibernate.ddl-auto=update", "--spring.jpa.open-in-view=false",
                        "--bootstrap.admin.email=", "--bootstrap.admin.password=",
                        "--spring.datasource.hikari.connection-timeout=10000");
    }
}
