package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.usecase.estoque.DefinirEstoqueMinimoUseCase;
import com.gestaocasas.application.usecase.estoque.ListarAlertasEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.ListarEstoqueImovelUseCase;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil local: as rotas de estoque ficam abertas enquanto não existe autenticação. */
@WebMvcTest(EstoqueImovelController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("local")
class EstoqueImovelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarEstoqueImovelUseCase listarEstoque;
    @MockitoBean
    private ListarAlertasEstoqueUseCase listarAlertas;
    @MockitoBean
    private DefinirEstoqueMinimoUseCase definirEstoqueMinimo;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();

    private EstoqueItemResponse item(int quantidade, int minimo, boolean abaixo) {
        return new EstoqueItemResponse(imovelId, produtoId, "COCA350", "7894900010015", "Coca-Cola Lata 350ml",
                "UN", true, quantidade, minimo, abaixo, Instant.now());
    }

    @Test
    void listaOEstoqueDoImovelComFiltros() throws Exception {
        when(listarEstoque.execute(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(item(20, 5, false)), 0, 5, 1, 1));

        mockMvc.perform(get("/imoveis/{id}/estoque", imovelId).param("nome", "coca").param("ativo", "true")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].imovelId").value(imovelId.toString()))
                .andExpect(jsonPath("$.data.content[0].produtoId").value(produtoId.toString()))
                .andExpect(jsonPath("$.data.content[0].quantidadeAtual").value(20))
                .andExpect(jsonPath("$.data.content[0].estoqueMinimo").value(5))
                .andExpect(jsonPath("$.data.content[0].abaixoDoMinimo").value(false));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(listarEstoque).execute(eq(imovelId), eq("coca"), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listaAlertas() throws Exception {
        when(listarAlertas.execute(eq(imovelId), any()))
                .thenReturn(new PageResponse<>(List.of(item(2, 5, true)), 0, 20, 1, 1));

        mockMvc.perform(get("/imoveis/{id}/estoque/alertas", imovelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].abaixoDoMinimo").value(true));
        verifyNoInteractions(listarEstoque);
    }

    @Test
    void defineEstoqueMinimo() throws Exception {
        when(definirEstoqueMinimo.execute(eq(imovelId), eq(produtoId), any())).thenReturn(item(3, 5, true));

        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estoqueMinimo").value(5));
    }

    @Test
    void rejeitaMinimoAusenteOuNegativo() throws Exception {
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("estoqueMinimo"));
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": -1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(definirEstoqueMinimo);
    }

    @Test
    void naoAceitaQuantidadeNoCorpo() throws Exception {
        when(definirEstoqueMinimo.execute(any(), any(), any())).thenReturn(item(3, 5, true));

        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estoqueMinimo\": 5, \"quantidadeAtual\": 999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantidadeAtual").value(3));
    }

    @Test
    void produtoQueNaoControlaEstoqueResponde422() throws Exception {
        when(definirEstoqueMinimo.execute(any(), any(), any()))
                .thenThrow(new RegraDeNegocioException("O produto 'Taxa de Limpeza' não controla estoque"));

        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 5}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("O produto 'Taxa de Limpeza' não controla estoque"));
    }

    @Test
    void imovelInexistenteResponde404() throws Exception {
        when(listarEstoque.execute(any(), any(), any(), any())).thenThrow(new ImovelNotFoundException(imovelId));

        mockMvc.perform(get("/imoveis/{id}/estoque", imovelId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/imoveis/abc/estoque")).andExpect(status().isBadRequest());
    }
}
