package com.restaurante.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.model.dto.request.LoginRequest;
import com.restaurante.model.dto.response.LoginResponse;
import com.restaurante.model.entity.UsuarioEntity;
import com.restaurante.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @ParameterizedTest
    @CsvSource(value = {"'', ''", "admin@example.test, ''", "'', password-test", "' ', password-test"})
    void sinAmbasVariablesNoCreaUsuario(String email, String password) {
        new AdminBootstrap(repository, encoder, email, password).run(null);
        verifyNoInteractions(repository);
    }

    @Test
    void almacenaSoloBcryptConRolExactoYActivo() {
        String password = "password-exclusivo-de-test";
        new AdminBootstrap(repository, encoder, " ADMIN@example.test ", password).run(null);
        var captor = ArgumentCaptor.forClass(UsuarioEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertEquals("admin@example.test", saved.getEmail());
        assertEquals(RolUsuario.ROLE_GERENTE, saved.getRol());
        assertTrue(saved.isActivo());
        assertNotEquals(password, saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2a$"));
        assertTrue(encoder.matches(password, saved.getPassword()));
    }

    @Test
    void emailExistenteNoSeSobrescribeNiSeReactiva() {
        var existing = UsuarioEntity.builder().email("admin@example.test").rol(RolUsuario.ROLE_CLIENTE)
                .password(encoder.encode("otra-password-test")).activo(false).build();
        when(repository.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(existing));
        new AdminBootstrap(repository, encoder, "admin@example.test", "password-nueva-test").run(null);
        verify(repository, never()).save(any());
        assertEquals(RolUsuario.ROLE_CLIENTE, existing.getRol());
        assertFalse(existing.isActivo());
        assertTrue(encoder.matches("otra-password-test", existing.getPassword()));
    }

    @Test
    void bootstrapRepetidoEsIdempotente() {
        var existing = UsuarioEntity.builder().email("admin@example.test").build();
        when(repository.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.empty(), Optional.of(existing));
        var bootstrap = new AdminBootstrap(repository, encoder, "admin@example.test", "password-test");
        bootstrap.run(null);
        bootstrap.run(null);
        verify(repository, times(1)).save(any());
    }

    @Test
    void rechazaPasswordQueBcryptTruncariaPorBytes() {
        assertThrows(IllegalArgumentException.class, () ->
                new AdminBootstrap(repository, encoder, "admin@example.test", "ñ".repeat(37)).run(null));
        verify(repository, never()).save(any());
    }

    @Test
    void representacionesYJsonNuncaExponenPasswordHashNiTokenEnToString() throws Exception {
        var user = UsuarioEntity.builder().email("admin@example.test").password(encoder.encode("password-test"))
                .rol(RolUsuario.ROLE_GERENTE).activo(true).build();
        String json = new ObjectMapper().writeValueAsString(user);
        assertFalse(json.contains("password"));
        assertFalse(json.contains(user.getPassword()));
        assertFalse(new LoginRequest("a@example.test", "password-test").toString().contains("password-test"));
        assertFalse(new LoginResponse("jwt-test", "Bearer", 1000).toString().contains("jwt-test"));
    }
}
