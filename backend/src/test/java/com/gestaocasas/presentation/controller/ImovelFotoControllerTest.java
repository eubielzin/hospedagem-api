package com.gestaocasas.presentation.controller;

import com.gestaocasas.ImagemFixtures;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.usecase.imovel.AddImovelFotoUseCase;
import com.gestaocasas.application.usecase.imovel.DefinirFotoPrincipalUseCase;
import com.gestaocasas.application.usecase.imovel.ExcluirImagemImovelUseCase;
import com.gestaocasas.application.usecase.imovel.ListarFotosImovelUseCase;
import com.gestaocasas.application.usecase.imovel.UploadImagemImovelUseCase;
import com.gestaocasas.domain.exception.ArquivoInvalidoException;
import com.gestaocasas.domain.exception.ArquivoInvalidoException.Motivo;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.StorageException;
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
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImovelFotoController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class ImovelFotoControllerTest {

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
    private final byte[] png = ImagemFixtures.png(800, 600);

    private MockMultipartFile arquivo() {
        return new MockMultipartFile("file", "../../casa da praia.png", "image/png", png);
    }

    @Test
    void uploadRetorna201NoEnvelopePadrao() throws Exception {
        when(uploadImagem.execute(imovelId, png, "image/png", "Fachada", true))
                .thenReturn(new ImovelFotoResponse(fotoId, "https://p.supabase.co/x.png", "Fachada", 1, true));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo())
                        .param("descricao", "Fachada").param("principal", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Imagem enviada com sucesso"))
                .andExpect(jsonPath("$.data.id").value(fotoId.toString()))
                .andExpect(jsonPath("$.data.url").value("https://p.supabase.co/x.png"))
                .andExpect(jsonPath("$.data.principal").value(true))
                .andExpect(jsonPath("$.data.ordem").value(1));
    }

    @Test
    void uploadSemCamposOpcionaisUsaPrincipalFalso() throws Exception {
        when(uploadImagem.execute(imovelId, png, "image/png", null, false))
                .thenReturn(new ImovelFotoResponse(fotoId, "https://p.supabase.co/x.png", null, 0, false));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadSemArquivoRetorna400() throws Exception {
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).param("descricao", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(uploadImagem);
    }

    @Test
    void uploadComJsonEmVezDeMultipartRetorna415() throws Exception {
        mockMvc.perform(post("/imoveis/{id}/fotos/upload", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadComDescricaoLongaRetorna400() throws Exception {
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo())
                        .param("descricao", "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("descricao")));
        verifyNoInteractions(uploadImagem);
    }

    @Test
    void errosDoArquivoViramStatusHttpApropriado() throws Exception {
        when(uploadImagem.execute(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new ArquivoInvalidoException(Motivo.MUITO_GRANDE, "Arquivo excede o tamanho máximo de 5 MB"))
                .thenThrow(new ArquivoInvalidoException(Motivo.TIPO_NAO_SUPORTADO, "Tipo de arquivo não permitido"))
                .thenThrow(new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO, "Arquivo vazio"));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().is(413));
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Arquivo vazio"));
    }

    @Test
    void uploadEmImovelInexistenteRetorna404() throws Exception {
        when(uploadImagem.execute(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new ImovelNotFoundException(imovelId));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().isNotFound());
    }

    @Test
    void falhaDoStorageRetorna502SemDetalhesInternos() throws Exception {
        when(uploadImagem.execute(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new StorageException("Upload recusado pelo armazenamento (HTTP 403)"));

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", imovelId).file(arquivo()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(not(containsString("403"))));
    }

    @Test
    void listaFotos() throws Exception {
        when(listarFotos.execute(imovelId)).thenReturn(List.of(
                new ImovelFotoResponse(fotoId, "https://exemplo.com/0.jpg", "Fachada", 0, true),
                new ImovelFotoResponse(UUID.randomUUID(), "https://exemplo.com/1.jpg", null, 1, false)));

        mockMvc.perform(get("/imoveis/{id}/fotos", imovelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].principal").value(true))
                .andExpect(jsonPath("$.data[0].descricao").value("Fachada"))
                .andExpect(jsonPath("$.data[1].ordem").value(1));
    }

    @Test
    void excluirRetorna204() throws Exception {
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", imovelId, fotoId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(excluirImagem).execute(imovelId, fotoId);
    }

    @Test
    void excluirFotoDeOutroImovelRetorna404() throws Exception {
        doThrow(new ImovelFotoNotFoundException(fotoId)).when(excluirImagem).execute(imovelId, fotoId);

        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", imovelId, fotoId)).andExpect(status().isNotFound());
    }

    @Test
    void excluirComStorageForaDoArRetorna502() throws Exception {
        doThrow(new StorageException("fora do ar")).when(excluirImagem).execute(imovelId, fotoId);

        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", imovelId, fotoId)).andExpect(status().isBadGateway());
    }

    @Test
    void definePrincipal() throws Exception {
        when(definirFotoPrincipal.execute(imovelId, fotoId))
                .thenReturn(new ImovelFotoResponse(fotoId, "https://exemplo.com/0.jpg", null, 0, true));

        mockMvc.perform(patch("/imoveis/{id}/fotos/{fotoId}/principal", imovelId, fotoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.principal").value(true));
    }

    @Test
    void fotoPorUrlContinuaFuncionando() throws Exception {
        when(addImovelFoto.execute(eq(imovelId), any()))
                .thenReturn(new ImovelFotoResponse(fotoId, "https://exemplo.com/a.jpg", null, 0, false));

        mockMvc.perform(post("/imoveis/{id}/fotos", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/a.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.principal").value(false));
    }

    @Test
    void rejeitaFotoComUrlInvalida() throws Exception {
        mockMvc.perform(post("/imoveis/{id}/fotos", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("url")));
        verifyNoInteractions(addImovelFoto);
    }
}
