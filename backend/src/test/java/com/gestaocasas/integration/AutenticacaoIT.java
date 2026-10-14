package com.gestaocasas.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo com tokens reais: administrador inicial criado na subida, login, acesso às rotas
 * administrativas, cadastro de outro usuário e troca de senha.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutenticacaoIT {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
        registry.add("app.auth.admin-inicial.email", () -> "Admin@Exemplo.com");
        registry.add("app.auth.admin-inicial.senha", () -> "senhaInicial123");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String login(String email, String senha) throws Exception {
        String corpo = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.token");
    }

    @Test
    void fluxoCompleto() throws Exception {
        // Criado na subida, com o e-mail em minúsculas e a senha somente como hash.
        assertThat(jdbc.queryForObject("SELECT email FROM usuario", String.class)).isEqualTo("admin@exemplo.com");
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuario", String.class)).startsWith("$2a$");

        mockMvc.perform(get("/produtos")).andExpect(status().isUnauthorized());

        String token = login("ADMIN@exemplo.com", "senhaInicial123");
        String bearer = "Bearer " + token;

        mockMvc.perform(get("/produtos").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@exemplo.com"))
                .andExpect(jsonPath("$.data.nome").value("Administrador"));

        mockMvc.perform(post("/usuarios").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Bruno\",\"email\":\"bruno@exemplo.com\",\"senha\":\"senhaBruno123\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/usuarios").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outro\",\"email\":\"BRUNO@exemplo.com\",\"senha\":\"senhaBruno123\"}"))
                .andExpect(status().isConflict());
        login("bruno@exemplo.com", "senhaBruno123");

        mockMvc.perform(patch("/auth/senha").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senhaInicial123\",\"novaSenha\":\"senhaNova456\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemplo.com\",\"senha\":\"senhaInicial123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
        login("admin@exemplo.com", "senhaNova456");

        // Usuário inativo não entra, mesmo com a senha certa.
        jdbc.update("UPDATE usuario SET ativo = false WHERE email = 'bruno@exemplo.com'");
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bruno@exemplo.com\",\"senha\":\"senhaBruno123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
