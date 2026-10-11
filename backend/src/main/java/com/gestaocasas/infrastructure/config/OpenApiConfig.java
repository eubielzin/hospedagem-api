package com.gestaocasas.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Gestão de Casas por Temporada")
                .description("API de gerenciamento de imóveis para locação por temporada")
                .version("0.0.1"));
    }
}
