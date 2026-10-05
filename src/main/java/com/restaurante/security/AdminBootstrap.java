package com.restaurante.security;

import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.model.entity.UsuarioEntity;
import com.restaurante.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UsuarioRepository repository, PasswordEncoder encoder,
            @Value("${bootstrap.admin.email:}") String email,
            @Value("${bootstrap.admin.password:}") String password) {
        this.repository = repository;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        if (repository.findByEmailIgnoreCase(normalizado).isPresent()) {
            return; // No restablece password ni cambia rol/activo de usuarios existentes.
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("BOOTSTRAP_ADMIN_PASSWORD excede el límite de 72 bytes de BCrypt");
        }
        repository.save(UsuarioEntity.builder().email(normalizado)
                .password(encoder.encode(password)).rol(RolUsuario.ROLE_GERENTE).activo(true).build());
    }
}
