package com.gestaocasas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Movimentações de estoque contra um PostgreSQL real: migração V6, entradas, ajustes, perdas,
 * transferências, estoque insuficiente, concorrência e imutabilidade do histórico. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class MovimentacaoEstoquePersistenceIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "12");
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

    private UUID criarProduto(String codigo, boolean controlaEstoque) throws Exception {
        return criar("/produtos", """
                {"codigo": "%s", "nome": "Produto %s", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": 6.00,
                 "controlaEstoque": %s}
                """.formatted(codigo, codigo, controlaEstoque));
    }

    private ResultActions entrada(UUID imovelId, UUID produtoId, int quantidade) throws Exception {
        return mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": \"%s\", \"quantidade\": %d, \"motivo\": \"Compra\"}"
                        .formatted(produtoId, quantidade)));
    }

    private ResultActions ajuste(UUID imovelId, UUID produtoId, String tipo, int quantidade) throws Exception {
        return mockMvc.perform(post("/imoveis/{id}/estoque/ajustes", imovelId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\": \"%s\", \"tipo\": \"%s\", \"quantidade\": %d, \"motivo\": \"Inventário\"}"
                        .formatted(produtoId, tipo, quantidade)));
    }

    private ResultActions transferir(UUID origem, UUID destino, UUID produtoId, int quantidade) throws Exception {
        return mockMvc.perform(post("/estoque/transferencias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"imovelOrigemId\": \"%s\", \"imovelDestinoId\": \"%s\", \"produtoId\": \"%s\", \"quantidade\": %d}"
                        .formatted(origem, destino, produtoId, quantidade)));
    }

    private int saldo(UUID imovelId, UUID produtoId) {
        List<Integer> saldos = jdbc.queryForList(
                "SELECT quantidade_atual FROM estoque_imovel WHERE imovel_id = ? AND produto_id = ?", Integer.class,
                imovelId, produtoId);
        return saldos.isEmpty() ? 0 : saldos.get(0);
    }

    private int movimentacoes(UUID imovelId, UUID produtoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM movimentacao_estoque WHERE imovel_id = ? AND produto_id = ?", Integer.class,
                imovelId, produtoId);
    }

    /** O último saldo_posterior do histórico tem de ser o saldo atual. */
    private void assertHistoricoFechaComOSaldo(UUID imovelId, UUID produtoId) {
        Integer soma = jdbc.queryForObject("""
                SELECT coalesce(sum(CASE WHEN saldo_posterior > saldo_anterior THEN quantidade ELSE -quantidade END), 0)
                FROM movimentacao_estoque WHERE imovel_id = ? AND produto_id = ?
                """, Integer.class, imovelId, produtoId);
        assertThat(soma).isEqualTo(saldo(imovelId, produtoId));
    }

    @Test
    void entradaAjusteEPerdaAlteramOSaldoERegistramOHistorico() throws Exception {
        UUID casa = criarImovel("Mov Casa 1");
        UUID produto = criarProduto("MOV-1", true);

        entrada(casa, produto, 24).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saldoAnterior").value(0))
                .andExpect(jsonPath("$.data.saldoPosterior").value(24));
        ajuste(casa, produto, "PERDA", 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saldoPosterior").value(22));
        ajuste(casa, produto, "AJUSTE_NEGATIVO", 3).andExpect(jsonPath("$.data.saldoPosterior").value(19));
        ajuste(casa, produto, "AJUSTE_POSITIVO", 1).andExpect(jsonPath("$.data.saldoPosterior").value(20));

        assertThat(saldo(casa, produto)).isEqualTo(20);
        assertHistoricoFechaComOSaldo(casa, produto);
        mockMvc.perform(get("/imoveis/{id}/estoque", casa).param("nome", "produto mov-1"))
                .andExpect(jsonPath("$.data.content[0].quantidadeAtual").value(20));
        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", casa).param("produtoId", produto.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].tipo").value("AJUSTE_POSITIVO"))
                .andExpect(jsonPath("$.data.content[3].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.data.content[3].motivo").value("Compra"));
        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", casa).param("tipo", "PERDA"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void saidaMaiorQueOSaldoERecusadaSemDeixarRastro() throws Exception {
        UUID casa = criarImovel("Mov Casa 2");
        UUID produto = criarProduto("MOV-2", true);
        entrada(casa, produto, 3).andExpect(status().isCreated());

        ajuste(casa, produto, "PERDA", 5).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(
                        "Estoque insuficiente de 'Produto MOV-2' neste imóvel: disponível 3, solicitado 5"));

        assertThat(saldo(casa, produto)).isEqualTo(3);
        assertThat(movimentacoes(casa, produto)).isEqualTo(1);
    }

    @Test
    void movimentarUmaCasaNaoAfetaAOutra() throws Exception {
        UUID casaA = criarImovel("Mov Casa A");
        UUID casaB = criarImovel("Mov Casa B");
        UUID produto = criarProduto("MOV-3", true);
        entrada(casaA, produto, 20).andExpect(status().isCreated());
        entrada(casaB, produto, 8).andExpect(status().isCreated());

        ajuste(casaA, produto, "PERDA", 5).andExpect(status().isCreated());
        // A Casa B tem 8: uma baixa de 10 não pode usar o saldo da Casa A.
        ajuste(casaB, produto, "PERDA", 10).andExpect(status().isUnprocessableContent());

        assertThat(saldo(casaA, produto)).isEqualTo(15);
        assertThat(saldo(casaB, produto)).isEqualTo(8);
        mockMvc.perform(get("/imoveis/{id}/estoque/movimentacoes", casaB))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].imovelId").value(casaB.toString()));
    }

    @Test
    void transferenciaMoveOSaldoEntreCasasComAMesmaReferencia() throws Exception {
        UUID origem = criarImovel("Mov Origem");
        UUID destino = criarImovel("Mov Destino");
        UUID produto = criarProduto("MOV-4", true);
        entrada(origem, produto, 20).andExpect(status().isCreated());

        String body = transferir(origem, destino, produto, 6).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saida.tipo").value("TRANSFERENCIA_SAIDA"))
                .andExpect(jsonPath("$.data.saida.saldoPosterior").value(14))
                .andExpect(jsonPath("$.data.entrada.tipo").value("TRANSFERENCIA_ENTRADA"))
                .andExpect(jsonPath("$.data.entrada.saldoPosterior").value(6))
                .andReturn().getResponse().getContentAsString();
        UUID transferenciaId = UUID.fromString(objectMapper.readTree(body).at("/data/transferenciaId").asString());

        assertThat(saldo(origem, produto)).isEqualTo(14);
        assertThat(saldo(destino, produto)).isEqualTo(6);
        Integer comAReferencia = jdbc.queryForObject("""
                SELECT count(*) FROM movimentacao_estoque
                WHERE referencia_id = ? AND referencia_tipo = 'TRANSFERENCIA'
                """, Integer.class, transferenciaId);
        assertThat(comAReferencia).isEqualTo(2);
    }

    @Test
    void transferenciaSemSaldoNaoAlteraNenhumaDasCasas() throws Exception {
        UUID origem = criarImovel("Mov Origem Sem Saldo");
        UUID destino = criarImovel("Mov Destino Sem Saldo");
        UUID produto = criarProduto("MOV-5", true);
        entrada(origem, produto, 2).andExpect(status().isCreated());

        transferir(origem, destino, produto, 3).andExpect(status().isUnprocessableContent());
        transferir(origem, origem, produto, 1).andExpect(status().isUnprocessableContent());
        transferir(origem, UUID.randomUUID(), produto, 1).andExpect(status().isNotFound());

        assertThat(saldo(origem, produto)).isEqualTo(2);
        assertThat(saldo(destino, produto)).isZero();
        assertThat(movimentacoes(origem, produto)).isEqualTo(1);
        assertThat(movimentacoes(destino, produto)).isZero();
    }

    @Test
    void produtoSemControleDeEstoqueOuInativoNaoRecebeEntrada() throws Exception {
        UUID casa = criarImovel("Mov Casa Regras");
        UUID taxa = criarProduto("MOV-TAXA", false);
        UUID inativo = criarProduto("MOV-INATIVO", true);
        entrada(casa, inativo, 4).andExpect(status().isCreated());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/produtos/{id}/inativar", inativo)).andExpect(status().isOk());

        entrada(casa, taxa, 1).andExpect(status().isUnprocessableContent());
        entrada(casa, inativo, 1).andExpect(status().isUnprocessableContent());
        entrada(casa, UUID.randomUUID(), 1).andExpect(status().isNotFound());
        entrada(UUID.randomUUID(), inativo, 1).andExpect(status().isNotFound());
        // O saldo que sobrou de um produto inativo ainda pode ser baixado.
        ajuste(casa, inativo, "PERDA", 4).andExpect(status().isCreated());
        assertThat(saldo(casa, inativo)).isZero();
    }

    @Test
    void baixasSimultaneasNuncaDeixamOSaldoNegativo() throws Exception {
        UUID casa = criarImovel("Mov Casa Concorrente");
        UUID produto = criarProduto("MOV-CNC", true);
        entrada(casa, produto, 10).andExpect(status().isCreated());

        List<Integer> respostas;
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Integer>> chamadas = IntStream.range(0, 25)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return ajuste(casa, produto, "AJUSTE_NEGATIVO", 1).andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor)).toList();
            respostas = chamadas.stream().map(CompletableFuture::join).toList();
        }

        assertThat(respostas).filteredOn(codigo -> codigo == 201).hasSize(10);
        assertThat(respostas).filteredOn(codigo -> codigo == 422).hasSize(15);
        assertThat(saldo(casa, produto)).isZero();
        assertThat(movimentacoes(casa, produto)).isEqualTo(11);
        assertHistoricoFechaComOSaldo(casa, produto);
    }

    @Test
    void transferenciasCruzadasSimultaneasNaoTravamNemPerdemUnidades() throws Exception {
        UUID casaA = criarImovel("Mov Cruzada A");
        UUID casaB = criarImovel("Mov Cruzada B");
        UUID produto = criarProduto("MOV-CRZ", true);
        entrada(casaA, produto, 50).andExpect(status().isCreated());
        entrada(casaB, produto, 50).andExpect(status().isCreated());

        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Integer>> chamadas = IntStream.range(0, 40)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return (i % 2 == 0 ? transferir(casaA, casaB, produto, 1) : transferir(casaB, casaA, produto, 1))
                                    .andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor)).toList();
            assertThat(chamadas.stream().map(CompletableFuture::join)).containsOnly(201);
        }

        assertThat(saldo(casaA, produto)).isEqualTo(50);
        assertThat(saldo(casaB, produto)).isEqualTo(50);
        assertHistoricoFechaComOSaldo(casaA, produto);
        assertHistoricoFechaComOSaldo(casaB, produto);
    }

    @Test
    void historicoNaoPodeSerAlteradoNemApagadoNoBanco() throws Exception {
        UUID casa = criarImovel("Mov Casa Imutavel");
        UUID produto = criarProduto("MOV-IMU", true);
        entrada(casa, produto, 5).andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("UPDATE movimentacao_estoque SET quantidade = 99 WHERE imovel_id = ?", casa))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("imutável");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM movimentacao_estoque WHERE imovel_id = ?", casa))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("imutável");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE movimentacao_estoque"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("imutável");
        assertThat(movimentacoes(casa, produto)).isEqualTo(1);
    }

    @Test
    void bancoRecusaMovimentacaoComAritmeticaErrada() throws Exception {
        UUID casa = criarImovel("Mov Casa Aritmetica");
        UUID produto = criarProduto("MOV-ARI", true);
        entrada(casa, produto, 5).andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO movimentacao_estoque (id, imovel_id, produto_id, tipo, quantidade, saldo_anterior,
                                                  saldo_posterior, criado_em)
                VALUES (gen_random_uuid(), ?, ?, 'ENTRADA', 3, 5, 9, now())
                """, casa, produto)).isInstanceOf(DataAccessException.class);
    }
}
