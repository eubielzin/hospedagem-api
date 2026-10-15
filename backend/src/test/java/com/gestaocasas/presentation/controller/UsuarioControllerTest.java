package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.dto.RedefinirSenhaRequest;
import com.gestaocasas.application.dto.UsuarioUpdateRequest;
import com.gestaocasas.application.usecase.usuario.*;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    @MockitoBean
    private GetUsuarioByIdUseCase getUsuarioById;
    @MockitoBean
    private UpdateUsuarioUseCase updateUsuario;
    @MockitoBean
    private AlterarSituacaoUsuarioUseCase alterarSituacao;
    @MockitoBean
    private RedefinirSenhaUseCase redefinirSenha;

    private final UUID id = UUID.randomUUID();
    private final UUID logado = UUID.randomUUID();

    private UsuarioResponse usuario(boolean ativo) {
        return new UsuarioResponse(id, "Bruno", "bruno@exemplo.com", ativo, Instant.now(), Instant.now());
    }

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

    @Test
    @WithMockUser
    void consultaPorId() throws Exception {
        when(getUsuarioById.execute(id)).thenReturn(usuario(true));
        when(getUsuarioById.execute(logado)).thenThrow(new UsuarioNotFoundException(logado));

        mockMvc.perform(get("/usuarios/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("bruno@exemplo.com"));
        mockMvc.perform(get("/usuarios/{id}", logado)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void atualizaNomeEEmail() throws Exception {
        when(updateUsuario.execute(eq(id), any())).thenReturn(usuario(true));

        mockMvc.perform(put("/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Bruno\",\"email\":\"bruno@exemplo.com\"}"))
                .andExpect(status().isOk());

        verify(updateUsuario).execute(id, new UsuarioUpdateRequest("Bruno", "bruno@exemplo.com"));
    }

    @Test
    @WithMockUser
    void atualizacaoValidaCampos() throws Exception {
        mockMvc.perform(put("/usuarios/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"email\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(updateUsuario);
    }

    @Test
    void inativaInformandoQuemEstaLogado() throws Exception {
        when(alterarSituacao.inativar(id, logado)).thenReturn(usuario(false));

        mockMvc.perform(patch("/usuarios/{id}/inativar", id).with(jwt().jwt(t -> t.subject(logado.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));
    }

    @Test
    void inativarOProprioUsuarioResponde422() throws Exception {
        when(alterarSituacao.inativar(logado, logado))
                .thenThrow(new RegraDeNegocioException("Você não pode inativar o próprio usuário"));

        mockMvc.perform(patch("/usuarios/{id}/inativar", logado).with(jwt().jwt(t -> t.subject(logado.toString()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Você não pode inativar o próprio usuário"));
    }

    @Test
    @WithMockUser
    void reativa() throws Exception {
        when(alterarSituacao.reativar(id)).thenReturn(usuario(true));

        mockMvc.perform(patch("/usuarios/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(true));
    }

    @Test
    @WithMockUser
    void redefineSenha() throws Exception {
        mockMvc.perform(patch("/usuarios/{id}/senha", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaSenha\":\"novaSenha456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso"));
        mockMvc.perform(patch("/usuarios/{id}/senha", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaSenha\":\"curta\"}"))
                .andExpect(status().isBadRequest());

        verify(redefinirSenha).execute(id, new RedefinirSenhaRequest("novaSenha456"));
    }
}
