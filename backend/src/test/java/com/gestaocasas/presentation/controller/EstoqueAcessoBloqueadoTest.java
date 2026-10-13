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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil de produção: sem autenticação na API, nenhuma rota de estoque responde. */
@WebMvcTest(EstoqueImovelController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("prod")
class EstoqueAcessoBloqueadoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarEstoqueImovelUseCase listarEstoque;
    @MockitoBean
    private ListarAlertasEstoqueUseCase listarAlertas;
    @MockitoBean
    private DefinirEstoqueMinimoUseCase definirEstoqueMinimo;

    @Test
    void rotasDeEstoqueRespondem403() throws Exception {
        UUID imovelId = UUID.randomUUID();

        mockMvc.perform(get("/imoveis/{id}/estoque", imovelId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/imoveis/{id}/estoque/alertas", imovelId)).andExpect(status().isForbidden());
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 5}"))
                .andExpect(status().isForbidden());
        // Rotas das próximas fases: já nascem protegidas.
        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", imovelId)).andExpect(status().isForbidden());
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId)).andExpect(status().isForbidden());
        mockMvc.perform(post("/estoque/transferencias")).andExpect(status().isForbidden());
        mockMvc.perform(post("/pdv/vendas")).andExpect(status().isForbidden());

        verifyNoInteractions(listarEstoque, listarAlertas, definirEstoqueMinimo);
    }
}
