package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.AjusteEstoqueRequest;
import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.TransferenciaEstoqueResponse;
import com.gestaocasas.application.usecase.estoque.ListarMovimentacoesEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.RegistrarAjusteEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.RegistrarEntradaEstoqueUseCase;
import com.gestaocasas.application.usecase.estoque.TransferirEstoqueUseCase;
import com.gestaocasas.domain.enums.TipoAjusteEstoque;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil local: as rotas de movimentação ficam abertas enquanto não existe autenticação. */
@WebMvcTest(MovimentacaoEstoqueController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("local")
class MovimentacaoEstoqueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListarMovimentacoesEstoqueUseCase listarMovimentacoes;
    @MockitoBean
    private RegistrarEntradaEstoqueUseCase registrarEntrada;
    @MockitoBean
    private RegistrarAjusteEstoqueUseCase registrarAjuste;
    @MockitoBean
    private TransferirEstoqueUseCase transferirEstoque;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();

    private MovimentacaoEstoqueResponse movimentacao(UUID imovel, TipoMovimentacao tipo, int anterior, int posterior,
                                                     UUID referencia) {
        return new MovimentacaoEstoqueResponse(UUID.randomUUID(), imovel, produtoId, "COCA350",
                "Coca-Cola Lata 350ml", tipo, Math.abs(posterior - anterior), anterior, posterior, "Motivo",
                referencia == null ? null : TipoReferenciaMovimentacao.TRANSFERENCIA, referencia, Instant.now());
    }

    @Test
    void registraEntrada() throws Exception {
        when(registrarEntrada.execute(eq(imovelId), any()))
                .thenReturn(movimentacao(imovelId, TipoMovimentacao.ENTRADA, 0, 24, null));

        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"quantidade\": 24}".formatted(produtoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.data.quantidade").value(24))
                .andExpect(jsonPath("$.data.saldoAnterior").value(0))
                .andExpect(jsonPath("$.data.saldoPosterior").value(24))
                .andExpect(jsonPath("$.data.imovelId").value(imovelId.toString()));
    }

    @Test
    void rejeitaEntradaSemProdutoOuComQuantidadeInvalida() throws Exception {
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("produtoId", "quantidade")));
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"quantidade\": 1.5}".formatted(produtoId)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrarEntrada);
    }

    @Test
    void registraPerdaPeloEndpointDeAjustes() throws Exception {
        when(registrarAjuste.execute(eq(imovelId), any()))
                .thenReturn(movimentacao(imovelId, TipoMovimentacao.PERDA, 10, 8, null));

        mockMvc.perform(post("/imoveis/{id}/estoque/ajustes", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"tipo\": \"PERDA\", \"quantidade\": 2, \"motivo\": \"Vencido\"}"
                                .formatted(produtoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tipo").value("PERDA"))
                .andExpect(jsonPath("$.data.saldoPosterior").value(8));

        ArgumentCaptor<AjusteEstoqueRequest> request = ArgumentCaptor.forClass(AjusteEstoqueRequest.class);
        verify(registrarAjuste).execute(eq(imovelId), request.capture());
        assertThat(request.getValue().tipo()).isEqualTo(TipoAjusteEstoque.PERDA);
        assertThat(request.getValue().motivo()).isEqualTo("Vencido");
    }

    @Test
    void ajusteExigeMotivoESoAceitaTiposDeAjuste() throws Exception {
        mockMvc.perform(post("/imoveis/{id}/estoque/ajustes", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"tipo\": \"AJUSTE_NEGATIVO\", \"quantidade\": 2, \"motivo\": \" \"}"
                                .formatted(produtoId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("motivo"));
        // Venda e transferência não podem ser lançadas como ajuste manual.
        for (String tipo : List.of("SAIDA_VENDA", "ENTRADA", "TRANSFERENCIA_SAIDA", "DEVOLUCAO")) {
            mockMvc.perform(post("/imoveis/{id}/estoque/ajustes", imovelId).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"produtoId\": \"%s\", \"tipo\": \"%s\", \"quantidade\": 2, \"motivo\": \"x\"}"
                                    .formatted(produtoId, tipo)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(registrarAjuste);
    }

    @Test
    void estoqueInsuficienteResponde422ComOSaldoDisponivel() throws Exception {
        when(registrarAjuste.execute(any(), any()))
                .thenThrow(new EstoqueInsuficienteException("Coca-Cola Lata 350ml", 3, 5));

        mockMvc.perform(post("/imoveis/{id}/estoque/ajustes", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"tipo\": \"PERDA\", \"quantidade\": 5, \"motivo\": \"x\"}"
                                .formatted(produtoId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Estoque insuficiente de 'Coca-Cola Lata 350ml' neste imóvel: disponível 3, solicitado 5"));
    }

    @Test
    void transfereEntreImoveis() throws Exception {
        UUID destino = UUID.randomUUID();
        UUID transferencia = UUID.randomUUID();
        when(transferirEstoque.execute(any())).thenReturn(new TransferenciaEstoqueResponse(transferencia,
                movimentacao(imovelId, TipoMovimentacao.TRANSFERENCIA_SAIDA, 20, 15, transferencia),
                movimentacao(destino, TipoMovimentacao.TRANSFERENCIA_ENTRADA, 8, 13, transferencia)));

        mockMvc.perform(post("/estoque/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelOrigemId\": \"%s\", \"imovelDestinoId\": \"%s\", \"produtoId\": \"%s\", \"quantidade\": 5}"
                                .formatted(imovelId, destino, produtoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transferenciaId").value(transferencia.toString()))
                .andExpect(jsonPath("$.data.saida.imovelId").value(imovelId.toString()))
                .andExpect(jsonPath("$.data.saida.saldoPosterior").value(15))
                .andExpect(jsonPath("$.data.entrada.imovelId").value(destino.toString()))
                .andExpect(jsonPath("$.data.entrada.saldoPosterior").value(13))
                .andExpect(jsonPath("$.data.entrada.referenciaTipo").value("TRANSFERENCIA"));
    }

    @Test
    void rejeitaTransferenciaIncompleta() throws Exception {
        mockMvc.perform(post("/estoque/transferencias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelOrigemId\": \"%s\", \"quantidade\": -1}".formatted(imovelId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("imovelDestinoId", "produtoId", "quantidade")));
        verifyNoInteractions(transferirEstoque);
    }

    @Test
    void listaMovimentacoesComFiltros() throws Exception {
        when(listarMovimentacoes.execute(any(), any(), any(), any())).thenReturn(new PageResponse<>(
                List.of(movimentacao(imovelId, TipoMovimentacao.ENTRADA, 0, 24, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", imovelId)
                        .param("produtoId", produtoId.toString()).param("tipo", "ENTRADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].produtoNome").value("Coca-Cola Lata 350ml"))
                .andExpect(jsonPath("$.data.content[0].criadoEm").exists());
        verify(listarMovimentacoes).execute(eq(imovelId), eq(produtoId), eq(TipoMovimentacao.ENTRADA), any());

        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", imovelId).param("tipo", "QUALQUER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historicoNaoPodeSerAlteradoNemApagadoPelaApi() throws Exception {
        UUID movimentacaoId = UUID.randomUUID();
        mockMvc.perform(put("/imoveis/{id}/estoque/movimentacoes/{mov}", imovelId, movimentacaoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/imoveis/{id}/estoque/movimentacoes/{mov}", imovelId, movimentacaoId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/imoveis/{id}/estoque/movimentacoes", imovelId))
                .andExpect(status().isMethodNotAllowed());
    }
}
