package com.restaurante.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import static org.mockito.Mockito.*;

class JwtAuthFilterTest {
    @AfterEach
    void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @Test
    void contextoYaAutenticadoNoSeCargaNiAutenticaDosVeces() throws Exception {
        var jwt = mock(JwtUtil.class);
        var users = mock(UsuarioDetailsService.class);
        var user = User.withUsername("a@example.test").password("hash-test").authorities("ROLE_GERENTE").build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer basura");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new JwtAuthFilter(jwt, users).doFilter(request, response, chain);
        verifyNoInteractions(jwt, users);
        verify(chain).doFilter(request, response);
    }
}
