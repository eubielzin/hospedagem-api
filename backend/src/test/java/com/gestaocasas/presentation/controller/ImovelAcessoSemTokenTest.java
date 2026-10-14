package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.usecase.imovel.*;
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

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A leitura de imóveis é pública; cadastrar, alterar e inativar exigem token. */
@WebMvcTest(ImovelController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class ImovelAcessoSemTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateImovelUseCase createImovel;
    @MockitoBean
    private ListImoveisUseCase listImoveis;
    @MockitoBean
    private GetImovelByIdUseCase getImovelById;
    @MockitoBean
    private UpdateImovelUseCase updateImovel;
    @MockitoBean
    private InativarImovelUseCase inativarImovel;

    private final UUID id = UUID.randomUUID();

    @Test
    void leituraEPublica() throws Exception {
        mockMvc.perform(get("/imoveis")).andExpect(status().isOk());
        mockMvc.perform(get("/imoveis/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void escritaResponde401() throws Exception {
        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/imoveis/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/imoveis/{id}/inativar", id)).andExpect(status().isUnauthorized());

        verifyNoInteractions(createImovel, updateImovel, inativarImovel);
    }
}
