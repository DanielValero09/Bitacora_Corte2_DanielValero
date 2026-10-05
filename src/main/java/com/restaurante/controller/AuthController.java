package com.restaurante.controller;

import com.restaurante.model.domain.enums.RolUsuario;
import com.restaurante.model.dto.request.LoginRequest;
import com.restaurante.model.dto.response.LoginResponse;
import com.restaurante.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
        type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwt;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Intercambia email y contraseña por un JWT Bearer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWT generado"),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new BadCredentialsException("Credenciales inválidas");
            }
            var auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
            var rol = RolUsuario.valueOf(auth.getAuthorities().iterator().next().getAuthority());
            String token = jwt.generateToken(auth.getName(), rol);
            log.info("Login exitoso: usuario={}", auth.getName());
            return new LoginResponse(token, "Bearer", jwt.getExpirationMs());
        } catch (AuthenticationException exception) {
            log.warn("Login fallido: credenciales inválidas");
            throw exception;
        }
    }
}
