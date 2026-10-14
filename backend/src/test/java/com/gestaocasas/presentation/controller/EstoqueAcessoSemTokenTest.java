package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.usecase.estoque.DefinirEstoqueMinimoUseCase;
import com.gestaocasas.application.usecase.estoque.ListarAlertasEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.ListarEstoqueImovelUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Estoque, movimentações e PDV são administrativos: sem token, respondem 401. */
@WebMvcTest(EstoqueImovelController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class EstoqueAcessoSemTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarEstoqueImovelUseCase listarEstoque;
    @MockitoBean
    private ListarAlertasEstoqueUseCase listarAlertas;
    @MockitoBean
    private DefinirEstoqueMinimoUseCase definirEstoqueMinimo;

    @Test
    void rotasDeEstoqueEPdvRespondem401SemToken() throws Exception {
        UUID imovelId = UUID.randomUUID();

        mockMvc.perform(get("/imoveis/{id}/estoque", imovelId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/imoveis/{id}/estoque/alertas", imovelId)).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 5}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", imovelId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/estoque/transferencias")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/pdv/vendas")).andExpect(status().isUnauthorized());

        verifyNoInteractions(listarEstoque, listarAlertas, definirEstoqueMinimo);
    }
}
