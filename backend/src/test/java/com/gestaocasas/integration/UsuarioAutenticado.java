package com.gestaocasas.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Toda requisição do MockMvc sai autenticada. Diferente de {@code @WithMockUser}, que vale só para a
 * thread do teste, isto cobre também as requisições disparadas em paralelo pelos testes de concorrência.
 */
@TestConfiguration
class UsuarioAutenticado {

    @Bean
    MockMvcBuilderCustomizer requisicoesAutenticadas() {
        return builder -> builder.defaultRequest(get("/").with(user("teste")));
    }
}
