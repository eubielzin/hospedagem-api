package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ItemVendaResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaRequest;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.dto.VendaResumoResponse;
import com.gestaocasas.application.usecase.pdv.CancelarVendaUseCase;
import com.gestaocasas.application.usecase.pdv.DevolverItensVendaUseCase;
import com.gestaocasas.application.usecase.pdv.GetVendaByIdUseCase;
import com.gestaocasas.application.usecase.pdv.ListarVendasUseCase;
import com.gestaocasas.application.usecase.pdv.RegistrarVendaUseCase;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoVenda;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.domain.exception.VendaNotFoundException;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Perfil local: as rotas do PDV ficam abertas enquanto não existe autenticação. */
@WebMvcTest(PdvController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("local")
@TestPropertySource(properties = "app.cors.allowed-origins=https://painel.exemplo.com")
class PdvControllerTest {

    private static final String CHAVE = "venda-2026-0001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarVendaUseCase registrarVenda;
    @MockitoBean
    private ListarVendasUseCase listarVendas;
    @MockitoBean
    private GetVendaByIdUseCase getVendaById;
    @MockitoBean
    private CancelarVendaUseCase cancelarVenda;
    @MockitoBean
    private DevolverItensVendaUseCase devolverItens;

    private final UUID vendaId = UUID.randomUUID();
    private final UUID imovelId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();

    private String corpo() {
        return "{\"imovelId\": \"%s\", \"itens\": [{\"produtoId\": \"%s\", \"quantidade\": 3}]}"
                .formatted(imovelId, produtoId);
    }

    private VendaResponse venda(StatusVenda status) {
        return new VendaResponse(vendaId, imovelId, TipoVenda.AVULSA, status, StatusPagamento.PENDENTE,
                new BigDecimal("18.00"), new BigDecimal("0.00"), null,
                List.of(new ItemVendaResponse(UUID.randomUUID(), produtoId, "COCA350", "Coca-Cola Lata 350ml", 3, 0,
                        new BigDecimal("6.00"), new BigDecimal("18.00"))),
                Instant.now(), status == StatusVenda.CANCELADA ? Instant.now() : null,
                status == StatusVenda.CANCELADA ? "Lançada errada" : null);
    }

    @Test
    void registraVendaComPrecosDoBackend() throws Exception {
        when(registrarVenda.execute(any(), eq(CHAVE))).thenReturn(ResultadoIdempotente.novo(venda(StatusVenda.CONCLUIDA)));

        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(vendaId.toString()))
                .andExpect(jsonPath("$.data.imovelId").value(imovelId.toString()))
                .andExpect(jsonPath("$.data.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.data.pagamentoStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.data.total").value(18.00))
                .andExpect(jsonPath("$.data.itens[0].precoUnitario").value(6.00))
                .andExpect(jsonPath("$.data.itens[0].subtotal").value(18.00));
    }

    @Test
    void precoTotalEStatusEnviadosPeloClienteSaoIgnorados() throws Exception {
        when(registrarVenda.execute(any(), any())).thenReturn(ResultadoIdempotente.novo(venda(StatusVenda.CONCLUIDA)));
        String adulterado = """
                {"imovelId": "%s", "total": 0.01, "pagamentoStatus": "RECEBIDO", "status": "CANCELADA",
                 "itens": [{"produtoId": "%s", "quantidade": 3, "precoUnitario": 0.01, "subtotal": 0.03}]}
                """.formatted(imovelId, produtoId);

        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(adulterado))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.total").value(18.00))
                .andExpect(jsonPath("$.data.pagamentoStatus").value("PENDENTE"));

        // O que chega ao caso de uso só tem imóvel, itens (produto e quantidade), observação e total esperado.
        ArgumentCaptor<VendaRequest> request = ArgumentCaptor.forClass(VendaRequest.class);
        verify(registrarVenda).execute(request.capture(), eq(CHAVE));
        assertThat(request.getValue().itens()).singleElement()
                .satisfies(item -> assertThat(item.quantidade()).isEqualTo(3));
        assertThat(request.getValue().totalEsperado()).isNull();
    }

    @Test
    void requisicaoRepetidaEhSinalizada() throws Exception {
        when(registrarVenda.execute(any(), eq(CHAVE))).thenReturn(ResultadoIdempotente.repetido(venda(StatusVenda.CONCLUIDA)));

        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.message").value("Venda já registrada com esta chave"))
                .andExpect(jsonPath("$.data.id").value(vendaId.toString()));
    }

    @Test
    void vendaExigeChaveDeIdempotenciaValida() throws Exception {
        mockMvc.perform(post("/pdv/vendas").contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cabeçalho obrigatório ausente: Idempotency-Key"));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", "curta")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", "com espaço e ç inválidos")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrarVenda);
    }

    @Test
    void rejeitaVendaSemItensOuComQuantidadeInvalida() throws Exception {
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itens\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("imovelId", "itens")));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelId\": \"%s\", \"itens\": [{\"quantidade\": 0}]}".formatted(imovelId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("itens[0].produtoId", "itens[0].quantidade")));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelId\": \"%s\", \"itens\": [{\"produtoId\": \"%s\", \"quantidade\": 1.5}]}"
                                .formatted(imovelId, produtoId)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registrarVenda);
    }

    @Test
    void estoqueInsuficienteResponde422ETotalDivergenteResponde409() throws Exception {
        when(registrarVenda.execute(any(), eq(CHAVE)))
                .thenThrow(new EstoqueInsuficienteException("Coca-Cola Lata 350ml", 1, 3));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", containsString("disponível 1, solicitado 3")));

        when(registrarVenda.execute(any(), eq("venda-2026-0002")))
                .thenThrow(new ConflitoException("O total da venda é 18.00, diferente do total esperado 15.00."));
        mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", "venda-2026-0002")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo()))
                .andExpect(status().isConflict());
    }

    @Test
    void consultaEListaVendas() throws Exception {
        when(getVendaById.execute(vendaId)).thenReturn(venda(StatusVenda.CONCLUIDA));
        when(listarVendas.execute(any(), any(), any())).thenReturn(new PageResponse<>(List.of(
                new VendaResumoResponse(vendaId, imovelId, TipoVenda.AVULSA, StatusVenda.CONCLUIDA,
                        StatusPagamento.PENDENTE, new BigDecimal("18.00"), Instant.now(), null)), 0, 20, 1, 1));

        mockMvc.perform(get("/pdv/vendas/{id}", vendaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itens[0].produtoNome").value("Coca-Cola Lata 350ml"));
        mockMvc.perform(get("/pdv/vendas").param("imovelId", imovelId.toString()).param("status", "CONCLUIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].total").value(18.00))
                .andExpect(jsonPath("$.data.content[0].itens").doesNotExist());
        verify(listarVendas).execute(eq(imovelId), eq(StatusVenda.CONCLUIDA), any());

        UUID inexistente = UUID.randomUUID();
        when(getVendaById.execute(inexistente)).thenThrow(new VendaNotFoundException(inexistente));
        mockMvc.perform(get("/pdv/vendas/{id}", inexistente)).andExpect(status().isNotFound());
        mockMvc.perform(get("/pdv/vendas").param("status", "RECEBIDA")).andExpect(status().isBadRequest());
    }

    @Test
    void cancelaVendaComMotivoObrigatorio() throws Exception {
        when(cancelarVenda.execute(eq(vendaId), any())).thenReturn(venda(StatusVenda.CANCELADA));

        mockMvc.perform(post("/pdv/vendas/{id}/cancelar", vendaId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lançada errada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"))
                .andExpect(jsonPath("$.data.motivoCancelamento").value("Lançada errada"));
        mockMvc.perform(post("/pdv/vendas/{id}/cancelar", vendaId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("motivo"));
    }

    @Test
    void registraDevolucaoComChaveEMotivo() throws Exception {
        when(devolverItens.execute(eq(vendaId), any(), eq(CHAVE)))
                .thenReturn(ResultadoIdempotente.novo(venda(StatusVenda.CONCLUIDA)));
        String corpo = "{\"motivo\": \"Cliente desistiu\", \"itens\": [{\"produtoId\": \"%s\", \"quantidade\": 1}]}"
                .formatted(produtoId);

        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"));
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", CHAVE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itens\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("itens", "motivo")));
    }

    @Test
    void naoExisteRotaParaMarcarVendaComoRecebidaNemParaAlterarOuApagar() throws Exception {
        mockMvc.perform(post("/pdv/vendas/{id}/receber", vendaId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/pdv/vendas/{id}/pagamento", vendaId)).andExpect(status().isNotFound());
        mockMvc.perform(put("/pdv/vendas/{id}", vendaId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/pdv/vendas/{id}", vendaId)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void corsPermiteOCabecalhoDeIdempotencia() throws Exception {
        mockMvc.perform(options("/pdv/vendas").header("Origin", "https://painel.exemplo.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Idempotency-Key")));
    }
}
