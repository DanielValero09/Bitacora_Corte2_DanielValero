package com.restaurante.security;

import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.model.entity.UsuarioEntity;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioEntityValidationTest {
    @Test
    void entidadRechazaPasswordEnClaroYAdmiteHashBcrypt() {
        var user = UsuarioEntity.builder().email("a@example.test").password("password-test")
                .rol(RolUsuario.ROLE_CLIENTE).activo(true).build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(user).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
            user.setPassword(new BCryptPasswordEncoder().encode("password-test"));
            assertTrue(validator.validate(user).isEmpty());
        }
    }

    @Test
    void callbacksJpaNormalizanEmailParaUnicidadSinDistinguirMayusculas() {
        var user = UsuarioEntity.builder().email(" ADMIN@Example.test ").build();
        ReflectionTestUtils.invokeMethod(user, "normalizarEmail");
        assertEquals("admin@example.test", user.getEmail());
    }
}
