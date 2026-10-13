package com.gestaocasas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estoque por imóvel contra um PostgreSQL real: migração V5, isolamento do saldo entre casas,
 * alertas de mínimo e constraints. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 * <p>
 * A quantidade é gravada direto no banco: as movimentações de estoque ainda não existem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EstoqueImovelPersistenceIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID criar(String rota, String json) throws Exception {
        String body = mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).at("/data/id").asString());
    }

    private UUID criarImovel(String nome) throws Exception {
        return criar("/imoveis", """
                {"nome": "%s", "logradouro": "Rua A", "cidade": "Ubatuba", "uf": "SP", "capacidadeMaxima": 4,
                 "quartos": 2, "banheiros": 1, "camas": 2, "valorBaseDiaria": 300.00,
                 "horarioCheckin": "15:00", "horarioCheckout": "11:00"}
                """.formatted(nome));
    }

    private UUID criarProduto(String codigo, String nome, boolean controlaEstoque) throws Exception {
        return criar("/produtos", """
                {"codigo": "%s", "nome": "%s", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": 6.00,
                 "controlaEstoque": %s}
                """.formatted(codigo, nome, controlaEstoque));
    }

    private void definirMinimo(UUID imovelId, UUID produtoId, int minimo) throws Exception {
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", imovelId, produtoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": " + minimo + "}"))
                .andExpect(status().isOk());
    }

    private void gravarQuantidade(UUID imovelId, UUID produtoId, int quantidade) {
        int linhas = jdbc.update("UPDATE estoque_imovel SET quantidade_atual = ? WHERE imovel_id = ? AND produto_id = ?",
                quantidade, imovelId, produtoId);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void cadaCasaTemSaldoIndependenteDoMesmoProduto() throws Exception {
        UUID casaA = criarImovel("Casa A");
        UUID casaB = criarImovel("Casa B");
        UUID casaC = criarImovel("Casa C");
        UUID coca = criarProduto("EST-COCA", "Estoque Coca-Cola", true);
        definirMinimo(casaA, coca, 0);
        definirMinimo(casaB, coca, 0);
        gravarQuantidade(casaA, coca, 20);
        gravarQuantidade(casaB, coca, 8);

        mockMvc.perform(get("/imoveis/{id}/estoque", casaA).param("nome", "estoque coca"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].quantidadeAtual").value(20))
                .andExpect(jsonPath("$.data.content[0].imovelId").value(casaA.toString()));
        mockMvc.perform(get("/imoveis/{id}/estoque", casaB).param("nome", "estoque coca"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].quantidadeAtual").value(8));
        // Casa que nunca teve o produto: aparece uma vez, zerado.
        mockMvc.perform(get("/imoveis/{id}/estoque", casaC).param("nome", "estoque coca"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].quantidadeAtual").value(0))
                .andExpect(jsonPath("$.data.content[0].atualizadoEm").doesNotExist());
    }

    @Test
    void listagemSoTrazProdutosQueControlamEstoqueEFiltraPorSituacao() throws Exception {
        UUID casa = criarImovel("Casa Filtro");
        criarProduto("FLT-AGUA", "Filtro Agua", true);
        UUID inativo = criarProduto("FLT-GELO", "Filtro Gelo", true);
        criarProduto("FLT-TAXA", "Filtro Taxa", false);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/produtos/{id}/inativar", inativo)).andExpect(status().isOk());

        mockMvc.perform(get("/imoveis/{id}/estoque", casa).param("nome", "filtro"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].nome").value("Filtro Agua"))
                .andExpect(jsonPath("$.data.content[1].produtoAtivo").value(false));
        mockMvc.perform(get("/imoveis/{id}/estoque", casa).param("nome", "filtro").param("ativo", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void alertasConsideramSomenteOSaldoDaPropriaCasa() throws Exception {
        UUID casaA = criarImovel("Casa Alerta A");
        UUID casaB = criarImovel("Casa Alerta B");
        UUID agua = criarProduto("ALR-AGUA", "Alerta Agua", true);
        UUID gelo = criarProduto("ALR-GELO", "Alerta Gelo", true);
        definirMinimo(casaA, agua, 5);
        definirMinimo(casaB, agua, 5);
        definirMinimo(casaA, gelo, 0);
        gravarQuantidade(casaA, agua, 5);
        gravarQuantidade(casaB, agua, 6);

        mockMvc.perform(get("/imoveis/{id}/estoque/alertas", casaA))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].nome").value("Alerta Agua"))
                .andExpect(jsonPath("$.data.content[0].abaixoDoMinimo").value(true));
        mockMvc.perform(get("/imoveis/{id}/estoque/alertas", casaB))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void definirMinimoNaoAlteraAQuantidade() throws Exception {
        UUID casa = criarImovel("Casa Minimo");
        UUID produto = criarProduto("MIN-COCA", "Minimo Coca", true);
        definirMinimo(casa, produto, 2);
        gravarQuantidade(casa, produto, 12);

        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", casa, produto)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantidadeAtual").value(12))
                .andExpect(jsonPath("$.data.estoqueMinimo").value(4));
    }

    @Test
    void recusaProdutoSemControleDeEstoqueEImovelInexistente() throws Exception {
        UUID casa = criarImovel("Casa Regra");
        UUID taxa = criarProduto("REG-TAXA", "Regra Taxa", false);

        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", casa, taxa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 1}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/imoveis/{id}/estoque", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(put("/imoveis/{id}/estoque/{produtoId}", casa, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estoqueMinimo\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requisicoesSimultaneasCriamUmaUnicaLinhaDeSaldo() throws Exception {
        UUID casa = criarImovel("Casa Concorrente");
        UUID produto = criarProduto("CNC-COCA", "Concorrente Coca", true);

        CompletableFuture<?>[] chamadas = IntStream.rangeClosed(1, 12)
                .mapToObj(minimo -> CompletableFuture.runAsync(() -> {
                    try {
                        definirMinimo(casa, produto, minimo);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(chamadas).join();

        Integer linhas = jdbc.queryForObject(
                "SELECT count(*) FROM estoque_imovel WHERE imovel_id = ? AND produto_id = ?", Integer.class,
                casa, produto);
        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void bancoRecusaSaldoNegativoEParDuplicado() throws Exception {
        UUID casa = criarImovel("Casa Constraint");
        UUID produto = criarProduto("CK-COCA", "Constraint Coca", true);
        definirMinimo(casa, produto, 0);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE estoque_imovel SET quantidade_atual = -1 WHERE imovel_id = ? AND produto_id = ?", casa, produto))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO estoque_imovel (id, imovel_id, produto_id, criado_em, atualizado_em)
                VALUES (gen_random_uuid(), ?, ?, now(), now())
                """, casa, produto)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
