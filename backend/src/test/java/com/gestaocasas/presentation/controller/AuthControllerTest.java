package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.AlterarSenhaRequest;
import com.gestaocasas.application.dto.LoginRequest;
import com.gestaocasas.application.dto.LoginResponse;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.usecase.usuario.AlterarSenhaUseCase;
import com.gestaocasas.application.usecase.usuario.GetUsuarioAtualUseCase;
import com.gestaocasas.application.usecase.usuario.LoginUseCase;
import com.gestaocasas.domain.exception.CredenciaisInvalidasException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase login;
    @MockitoBean
    private GetUsuarioAtualUseCase getUsuarioAtual;
    @MockitoBean
    private AlterarSenhaUseCase alterarSenha;

    private final UUID usuarioId = UUID.randomUUID();

    private UsuarioResponse usuario() {
        return new UsuarioResponse(usuarioId, "Ana", "ana@exemplo.com", true, Instant.now(), Instant.now());
    }

    @Test
    void loginEPublicoEDevolveToken() throws Exception {
        Instant expiraEm = Instant.parse("2026-10-14T20:00:00Z");
        when(login.execute(any())).thenReturn(new LoginResponse("abc.def.ghi", "Bearer", expiraEm, usuario()));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@exemplo.com\",\"senha\":\"segredo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("abc.def.ghi"))
                .andExpect(jsonPath("$.data.tipo").value("Bearer"))
                .andExpect(jsonPath("$.data.expiraEm").value("2026-10-14T20:00:00Z"))
                .andExpect(jsonPath("$.data.usuario.email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.data.usuario.senhaHash").doesNotExist());

        verify(login).execute(new LoginRequest("ana@exemplo.com", "segredo123"));
    }

    @Test
    void credenciaisInvalidasRespondem401() throws Exception {
        when(login.execute(any())).thenThrow(new CredenciaisInvalidasException());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@exemplo.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void loginSemCamposResponde400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));

        verifyNoInteractions(login);
    }

    @Test
    void meDevolveODonoDoToken() throws Exception {
        when(getUsuarioAtual.execute(usuarioId)).thenReturn(usuario());

        mockMvc.perform(get("/auth/me").with(jwt().jwt(token -> token.subject(usuarioId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(usuarioId.toString()));
    }

    @Test
    void meSemTokenResponde401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        verifyNoInteractions(getUsuarioAtual);
    }

    @Test
    void alteraSenhaDoDonoDoToken() throws Exception {
        mockMvc.perform(patch("/auth/senha").with(jwt().jwt(token -> token.subject(usuarioId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"segredo123\",\"novaSenha\":\"novaSenha456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha alterada com sucesso"));

        verify(alterarSenha).execute(usuarioId, new AlterarSenhaRequest("segredo123", "novaSenha456"));
    }

    @Test
    void senhaAtualIncorretaResponde422() throws Exception {
        doThrow(new RegraDeNegocioException("Senha atual incorreta"))
                .when(alterarSenha).execute(eq(usuarioId), any());

        mockMvc.perform(patch("/auth/senha").with(jwt().jwt(token -> token.subject(usuarioId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"errada123\",\"novaSenha\":\"novaSenha456\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Senha atual incorreta"));
    }

    @Test
    void novaSenhaCurtaResponde400() throws Exception {
        mockMvc.perform(patch("/auth/senha").with(jwt().jwt(token -> token.subject(usuarioId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"segredo123\",\"novaSenha\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("novaSenha"));

        verifyNoInteractions(alterarSenha);
    }
}
