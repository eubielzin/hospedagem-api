package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.usecase.usuario.CreateUsuarioUseCase;
import com.gestaocasas.application.usecase.usuario.ListUsuariosUseCase;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class UsuarioControllerTest {

    private static final String VALIDO = """
            {"nome":"Bruno","email":"bruno@exemplo.com","senha":"segredo123"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateUsuarioUseCase createUsuario;
    @MockitoBean
    private ListUsuariosUseCase listUsuarios;

    @Test
    @WithMockUser
    void criaUsuario() throws Exception {
        when(createUsuario.execute(any())).thenReturn(new UsuarioResponse(UUID.randomUUID(), "Bruno",
                "bruno@exemplo.com", true, Instant.now(), Instant.now()));

        mockMvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("bruno@exemplo.com"))
                .andExpect(jsonPath("$.data.senha").doesNotExist());
    }

    @Test
    @WithMockUser
    void emailDuplicadoResponde409() throws Exception {
        when(createUsuario.execute(any())).thenThrow(new ConflitoException("Já existe um usuário com o e-mail x"));

        mockMvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void validaCampos() throws Exception {
        mockMvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"email\":\"nao-e-email\",\"senha\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));

        verifyNoInteractions(createUsuario);
    }

    @Test
    @WithMockUser
    void listaUsuarios() throws Exception {
        when(listUsuarios.execute()).thenReturn(List.of());

        mockMvc.perform(get("/usuarios")).andExpect(status().isOk());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());

        verifyNoInteractions(createUsuario, listUsuarios);
    }
}
