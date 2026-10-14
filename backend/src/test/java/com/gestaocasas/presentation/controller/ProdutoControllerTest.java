package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.ProdutoBuscaResponse;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.usecase.produto.*;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdutoController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class ProdutoControllerTest {

    private static final String VALIDO = """
            {
              "codigo": "COCA350",
              "codigoBarras": "7894900010015",
              "nome": "Coca-Cola Lata 350ml",
              "categoria": "CONSUMO",
              "unidade": "UN",
              "precoCusto": 3.20,
              "precoVenda": 6.00,
              "controlaEstoque": true
            }
            """;

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

    private static ProdutoResponse response(UUID id, boolean ativo) {
        return new ProdutoResponse(id, "COCA350", "7894900010015", "Coca-Cola Lata 350ml", null,
                CategoriaProduto.CONSUMO, "UN", new BigDecimal("3.20"), new BigDecimal("6.00"), true, ativo,
                Instant.now(), Instant.now());
    }

    @Test
    void criaProdutoERetornaEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(createProduto.execute(any())).thenReturn(response(id, true));

        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.precoVenda").value(6.00))
                .andExpect(jsonPath("$.data.categoria").value("CONSUMO"))
                .andExpect(jsonPath("$.data.controlaEstoque").value(true))
                .andExpect(jsonPath("$.data.ativo").value(true))
                .andExpect(jsonPath("$.data.quantidade").doesNotExist());
    }

    @Test
    void rejeitaProdutoInvalidoComErrosPorCampo() throws Exception {
        String invalido = """
                {
                  "codigo": " ",
                  "nome": "",
                  "unidade": "UNIDADE-LONGA",
                  "precoCusto": -1,
                  "precoVenda": 6.999
                }
                """;

        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("codigo", "nome", "categoria",
                        "unidade", "precoCusto", "precoVenda", "controlaEstoque")));
        verifyNoInteractions(createProduto);
    }

    @Test
    void rejeitaCategoriaDesconhecida() throws Exception {
        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("CONSUMO", "BEBIDA")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(createProduto);
    }

    @Test
    void retorna409ComMensagemQuandoCodigoJaExiste() throws Exception {
        when(createProduto.execute(any()))
                .thenThrow(new ConflitoException("Já existe um produto com o código 'COCA350'"));

        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Já existe um produto com o código 'COCA350'"));
    }

    @Test
    void retorna404QuandoProdutoNaoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(getProdutoById.execute(id)).thenThrow(new ProdutoNotFoundException(id));

        mockMvc.perform(get("/produtos/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Produto não encontrado: " + id));
    }

    @Test
    void retorna400ParaIdQueNaoEhUuid() throws Exception {
        mockMvc.perform(get("/produtos/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void repassaFiltrosEPaginacaoNaListagem() throws Exception {
        when(listProdutos.execute(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/produtos").param("nome", "coca").param("categoria", "CONSUMO")
                        .param("ativo", "true").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(listProdutos).execute(eq("coca"), eq(CategoriaProduto.CONSUMO), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("nome")).isNotNull();
    }

    @Test
    void rejeitaFiltroDeCategoriaInvalido() throws Exception {
        mockMvc.perform(get("/produtos").param("categoria", "QUALQUER")).andExpect(status().isBadRequest());
    }

    @Test
    void atualizaEInativa() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateProduto.execute(eq(id), any())).thenReturn(response(id, true));
        when(inativarProduto.execute(id)).thenReturn(response(id, false));

        mockMvc.perform(put("/produtos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        mockMvc.perform(patch("/produtos/{id}/inativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));
    }

    @Test
    void naoExisteExclusaoFisicaDeProduto() throws Exception {
        mockMvc.perform(delete("/produtos/{id}", UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void buscaDinamicaNaoEhConfundidaComAConsultaPorId() throws Exception {
        UUID imovelId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        when(buscarProdutos.execute(any(), any(), anyBoolean(), anyInt())).thenReturn(List.of(
                new ProdutoBuscaResponse(produtoId, "COCA350", "7894900010015", "Coca-Cola Lata 350ml",
                        CategoriaProduto.CONSUMO, "UN", new BigDecimal("6.00"), true, true, 20, false)));

        mockMvc.perform(get("/produtos/buscar").param("termo", "coca").param("imovelId", imovelId.toString())
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Produtos encontrados"))
                .andExpect(jsonPath("$.data[0].id").value(produtoId.toString()))
                .andExpect(jsonPath("$.data[0].nome").value("Coca-Cola Lata 350ml"))
                .andExpect(jsonPath("$.data[0].precoVenda").value(6.00))
                .andExpect(jsonPath("$.data[0].quantidadeDisponivel").value(20))
                .andExpect(jsonPath("$.data[0].controlaEstoque").value(true))
                .andExpect(jsonPath("$.data[0].ativo").value(true))
                .andExpect(jsonPath("$.data[0].semEstoque").value(false))
                .andExpect(jsonPath("$.data[0].precoCusto").doesNotExist());
        verify(buscarProdutos).execute("coca", imovelId, true, 5);
        verifyNoInteractions(getProdutoById);
    }

    @Test
    void buscaAceitaUmCaractereEUsaPadroes() throws Exception {
        when(buscarProdutos.execute(any(), any(), anyBoolean(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/produtos/buscar").param("termo", "c").param("somenteAtivos", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(buscarProdutos).execute("c", null, false, 10);
    }

    @Test
    void buscaRejeitaParametrosInvalidos() throws Exception {
        mockMvc.perform(get("/produtos/buscar")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/produtos/buscar").param("termo", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("termo"));
        mockMvc.perform(get("/produtos/buscar").param("termo", "x".repeat(101))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/produtos/buscar").param("termo", "c").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        mockMvc.perform(get("/produtos/buscar").param("termo", "c").param("imovelId", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(buscarProdutos);
    }
}
