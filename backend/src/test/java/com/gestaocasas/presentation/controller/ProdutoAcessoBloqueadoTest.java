package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.usecase.produto.*;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil de produção: sem autenticação na API, nenhuma rota de produtos responde, nem as de
 * leitura (a listagem expõe preço de custo). Nenhum caso de uso é chamado.
 */
@WebMvcTest(ProdutoController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("prod")
class ProdutoAcessoBloqueadoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateProdutoUseCase createProduto;
    @MockitoBean
    private ListProdutosUseCase listProdutos;
    @MockitoBean
    private GetProdutoByIdUseCase getProdutoById;
    @MockitoBean
    private UpdateProdutoUseCase updateProduto;
    @MockitoBean
    private InativarProdutoUseCase inativarProduto;
    @MockitoBean
    private BuscarProdutosUseCase buscarProdutos;

    @Test
    void todasAsRotasDeProdutosRespondem403() throws Exception {
        UUID id = UUID.randomUUID();
        String corpo = "{\"codigo\":\"X\"}";

        mockMvc.perform(get("/produtos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
        mockMvc.perform(get("/produtos/{id}", id)).andExpect(status().isForbidden());
        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/produtos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/produtos/{id}/inativar", id)).andExpect(status().isForbidden());
        mockMvc.perform(get("/produtos/buscar").param("termo", "coca")).andExpect(status().isForbidden());

        verifyNoInteractions(createProduto, listProdutos, getProdutoById, updateProduto, inativarProduto,
                buscarProdutos);
    }
}
