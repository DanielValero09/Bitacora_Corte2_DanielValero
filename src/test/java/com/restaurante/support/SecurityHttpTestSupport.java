package com.restaurante.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.config.PasswordConfig;
import com.restaurante.config.SecurityConfig;
import com.restaurante.exception.GlobalExceptionHandler;
import com.restaurante.repository.UsuarioRepository;
import com.restaurante.security.*;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Conserva fixtures reales de negocio y añade filtros y proxies @PreAuthorize. */
public final class SecurityHttpTestSupport {
    private SecurityHttpTestSupport() {}

    public static Builder securedSetup(Object... controllers) {
        return new Builder(controllers);
    }

    public static void close(MockMvc mvc) {
        if (mvc != null) {
            ((GenericWebApplicationContext) mvc.getDispatcherServlet().getWebApplicationContext()).close();
        }
    }

    public static final class Builder {
        private final Object[] controllers;

        private Builder(Object[] controllers) {
            this.controllers = controllers;
        }

        public Builder setControllerAdvice(GlobalExceptionHandler advice) {
            return this; // El advice se registra como bean para todos los escenarios.
        }

        public MockMvc build() {
            var context = new GenericWebApplicationContext(new MockServletContext());
            try {
                context.getEnvironment().getPropertySources()
                        .addFirst(new ResourcePropertySource("classpath:application.properties"));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            var reader = new AnnotatedBeanDefinitionReader(context);
            reader.register(WebConfig.class, SecurityConfig.class, PasswordConfig.class,
                    JwtUtil.class, JwtAuthFilter.class, UsuarioDetailsService.class,
                    SecurityErrorHandler.class, GlobalExceptionHandler.class);
            context.registerBean(UsuarioRepository.class, () -> mock(UsuarioRepository.class));
            for (Object controller : controllers) {
                registerController(context, controller);
            }
            context.refresh();
            return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                    .defaultRequest(get("/").with(user("http-test").roles("GERENTE"))).build();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void registerController(GenericWebApplicationContext context, Object controller) {
            context.registerBean((Class) controller.getClass(), () -> controller);
        }
    }

    @TestConfiguration
    @EnableWebMvc
    static class WebConfig {
        @Bean
        ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json().build();
        }
    }
}
