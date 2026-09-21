package com.restaurante.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI americanBitesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("American Bites API")
                .description("API REST para la gestión del restaurante American Bites.")
                .version("v1"));
    }
}
