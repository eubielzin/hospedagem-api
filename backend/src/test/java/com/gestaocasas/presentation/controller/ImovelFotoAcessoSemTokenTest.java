package com.gestaocasas.presentation.controller;

import com.gestaocasas.ImagemFixtures;
import com.gestaocasas.application.usecase.imovel.AddImovelFotoUseCase;
import com.gestaocasas.application.usecase.imovel.DefinirFotoPrincipalUseCase;
import com.gestaocasas.application.usecase.imovel.ExcluirImagemImovelUseCase;
import com.gestaocasas.application.usecase.imovel.ListarFotosImovelUseCase;
import com.gestaocasas.application.usecase.imovel.UploadImagemImovelUseCase;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem token, a escrita de fotos responde 401 e nenhum caso de uso é chamado; a leitura é pública. */
@WebMvcTest(ImovelFotoController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class ImovelFotoAcessoSemTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddImovelFotoUseCase addImovelFoto;
    @MockitoBean
    private UploadImagemImovelUseCase uploadImagem;
    @MockitoBean
    private ListarFotosImovelUseCase listarFotos;
    @MockitoBean
    private ExcluirImagemImovelUseCase excluirImagem;
    @MockitoBean
    private DefinirFotoPrincipalUseCase definirFotoPrincipal;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID fotoId = UUID.randomUUID();

    @Test
    void escritaRespondem401() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("file", "a.png", "image/png", ImagemFixtures.png(10, 10));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(post("/imoveis/{id}/fotos", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://exemplo.com/a.png\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", imovelId, fotoId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/imoveis/{id}/fotos/{fotoId}/principal", imovelId, fotoId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(addImovelFoto, uploadImagem, excluirImagem, definirFotoPrincipal);
    }

    @Test
    void leituraContinuaPublica() throws Exception {
        when(listarFotos.execute(imovelId)).thenReturn(List.of());

        mockMvc.perform(get("/imoveis/{id}/fotos", imovelId)).andExpect(status().isOk());
    }
}
