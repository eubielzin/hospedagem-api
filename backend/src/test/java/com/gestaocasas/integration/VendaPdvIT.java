package com.gestaocasas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PDV contra um PostgreSQL real: migração V8, venda com baixa de estoque por casa, atomicidade,
 * preço histórico, cancelamento, devolução, concorrência e idempotência. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class VendaPdvIT {

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

    private static String produtoJson(String codigo, String preco, boolean controlaEstoque) {
        return """
                {"codigo": "%s", "nome": "Produto %s", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": %s,
                 "controlaEstoque": %s}
                """.formatted(codigo, codigo, preco, controlaEstoque);
    }

    private UUID criarProduto(String codigo, String preco, boolean controlaEstoque) throws Exception {
        return criar("/produtos", produtoJson(codigo, preco, controlaEstoque));
    }

    private void entrada(UUID imovelId, UUID produtoId, int quantidade) throws Exception {
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"quantidade\": %d}".formatted(produtoId, quantidade)))
                .andExpect(status().isCreated());
    }

    private static String vendaJson(UUID imovelId, String itens) {
        return "{\"imovelId\": \"%s\", \"itens\": [%s]}".formatted(imovelId, itens);
    }

    private static String item(UUID produtoId, int quantidade) {
        return "{\"produtoId\": \"%s\", \"quantidade\": %d}".formatted(produtoId, quantidade);
    }

    private ResultActions vender(String chave, String corpo) throws Exception {
        return mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private UUID venderComSucesso(UUID imovelId, String itens) throws Exception {
        String body = vender("it-" + UUID.randomUUID(), vendaJson(imovelId, itens)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).at("/data/id").asString());
    }

    private int saldo(UUID imovelId, UUID produtoId) {
        List<Integer> saldos = jdbc.queryForList(
                "SELECT quantidade_atual FROM estoque_imovel WHERE imovel_id = ? AND produto_id = ?", Integer.class,
                imovelId, produtoId);
        return saldos.isEmpty() ? 0 : saldos.get(0);
    }

    private int contar(String tabelaEFiltro, Object... parametros) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabelaEFiltro, Integer.class, parametros);
    }

    @Test
    void vendaBaixaOEstoqueSomenteDaCasaDaVenda() throws Exception {
        UUID casaA = criarImovel("PDV Casa A");
        UUID casaB = criarImovel("PDV Casa B");
        UUID coca = criarProduto("PDV-COCA", "6.00", true);
        UUID passeio = criarProduto("PDV-PASSEIO", "350.00", false);
        entrada(casaA, coca, 20);
        entrada(casaB, coca, 8);

        String body = vender("it-venda-casa-a", vendaJson(casaA, item(coca, 3) + "," + item(passeio, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imovelId").value(casaA.toString()))
                .andExpect(jsonPath("$.data.status").value("CONCLUIDA"))
                .andExpect(jsonPath("$.data.pagamentoStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.data.total").value(368.00))
                .andExpect(jsonPath("$.data.itens.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID vendaId = UUID.fromString(objectMapper.readTree(body).at("/data/id").asString());

        assertThat(saldo(casaA, coca)).isEqualTo(17);
        assertThat(saldo(casaB, coca)).isEqualTo(8);
        assertThat(contar("movimentacao_estoque WHERE referencia_id = ? AND tipo = 'SAIDA_VENDA' AND imovel_id = ?",
                vendaId, casaA)).isEqualTo(1);
        assertThat(contar("movimentacao_estoque WHERE referencia_id = ?", vendaId)).isEqualTo(1);
    }

    @Test
    void semSaldoParaUmItemNadaDaVendaEGravado() throws Exception {
        UUID casaA = criarImovel("PDV Atomica A");
        UUID casaB = criarImovel("PDV Atomica B");
        UUID agua = criarProduto("PDV-ATO-AGUA", "4.00", true);
        UUID gelo = criarProduto("PDV-ATO-GELO", "10.00", true);
        entrada(casaA, agua, 10);
        entrada(casaA, gelo, 1);
        entrada(casaB, gelo, 50);
        int vendasAntes = contar("venda");
        int itensAntes = contar("item_venda");

        // A Casa B tem 50 gelos, mas a venda é da Casa A, que tem 1.
        vender("it-atomica-0001", vendaJson(casaA, item(agua, 2) + "," + item(gelo, 2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(
                        "Estoque insuficiente de 'Produto PDV-ATO-GELO' neste imóvel: disponível 1, solicitado 2"));

        assertThat(saldo(casaA, agua)).isEqualTo(10);
        assertThat(saldo(casaA, gelo)).isEqualTo(1);
        assertThat(saldo(casaB, gelo)).isEqualTo(50);
        assertThat(contar("venda")).isEqualTo(vendasAntes);
        assertThat(contar("item_venda")).isEqualTo(itensAntes);
        assertThat(contar("movimentacao_estoque WHERE imovel_id = ? AND tipo = 'SAIDA_VENDA'", casaA)).isZero();
        // A tentativa que falhou não ocupa a chave: a mesma chave funciona depois de repor o estoque.
        entrada(casaA, gelo, 5);
        vender("it-atomica-0001", vendaJson(casaA, item(agua, 2) + "," + item(gelo, 2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"));
    }

    @Test
    void precoDaVendaNaoMudaQuandoOCadastroMuda() throws Exception {
        UUID casa = criarImovel("PDV Preco");
        UUID produto = criarProduto("PDV-PRECO", "6.00", true);
        entrada(casa, produto, 10);
        UUID vendaId = venderComSucesso(casa, item(produto, 2));

        mockMvc.perform(put("/produtos/{id}", produto).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson("PDV-PRECO-NOVO", "9.50", true).replace("Produto PDV-PRECO-NOVO", "Renomeado")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pdv/vendas/{id}", vendaId))
                .andExpect(jsonPath("$.data.total").value(12.00))
                .andExpect(jsonPath("$.data.itens[0].precoUnitario").value(6.00))
                .andExpect(jsonPath("$.data.itens[0].produtoNome").value("Produto PDV-PRECO"))
                .andExpect(jsonPath("$.data.itens[0].produtoCodigo").value("PDV-PRECO"));
        // Uma venda nova usa o preço novo.
        vender("it-preco-novo-01", vendaJson(casa, item(produto, 2)))
                .andExpect(jsonPath("$.data.total").value(19.00));
    }

    @Test
    void precoEnviadoPeloClienteNaoEUsadoETotalEsperadoDivergenteRecusaAVenda() throws Exception {
        UUID casa = criarImovel("PDV Total");
        UUID produto = criarProduto("PDV-TOTAL", "6.00", true);
        entrada(casa, produto, 10);

        vender("it-total-adulterado", """
                {"imovelId": "%s", "total": 0.01,
                 "itens": [{"produtoId": "%s", "quantidade": 2, "precoUnitario": 0.01, "subtotal": 0.02}]}
                """.formatted(casa, produto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.total").value(12.00));

        vender("it-total-divergente", """
                {"imovelId": "%s", "totalEsperado": 10.00, "itens": [%s]}
                """.formatted(casa, item(produto, 2)))
                .andExpect(status().isConflict());
        assertThat(saldo(casa, produto)).isEqualTo(8);
    }

    @Test
    void mesmaChaveRegistraUmaUnicaVenda() throws Exception {
        UUID casa = criarImovel("PDV Idempotente");
        UUID produto = criarProduto("PDV-IDEM", "6.00", true);
        entrada(casa, produto, 10);
        String corpo = vendaJson(casa, item(produto, 2));

        String primeira = vender("it-idem-0001", corpo).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andReturn().getResponse().getContentAsString();
        String segunda = vender("it-idem-0001", corpo).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        JsonNode idPrimeira = objectMapper.readTree(primeira).at("/data/id");
        assertThat(objectMapper.readTree(segunda).at("/data/id")).isEqualTo(idPrimeira);
        assertThat(saldo(casa, produto)).isEqualTo(8);
        // A mesma chave com outro conteúdo é recusada e não vende nada.
        vender("it-idem-0001", vendaJson(casa, item(produto, 5))).andExpect(status().isConflict());
        assertThat(saldo(casa, produto)).isEqualTo(8);
    }

    @Test
    void mesmaChaveEmRequisicoesSimultaneasRegistraUmaUnicaVenda() throws Exception {
        UUID casa = criarImovel("PDV Idempotente Concorrente");
        UUID produto = criarProduto("PDV-IDEM-CNC", "6.00", true);
        entrada(casa, produto, 50);
        String corpo = vendaJson(casa, item(produto, 1));

        List<String> ids;
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<String>> chamadas = IntStream.range(0, 20)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            String body = vender("it-idem-concorrente", corpo).andExpect(status().isCreated())
                                    .andReturn().getResponse().getContentAsString();
                            return objectMapper.readTree(body).at("/data/id").asString();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor)).toList();
            ids = chamadas.stream().map(CompletableFuture::join).toList();
        }

        assertThat(ids).hasSize(20);
        assertThat(ids.stream().distinct()).hasSize(1);
        assertThat(saldo(casa, produto)).isEqualTo(49);
    }

    @Test
    void vendasSimultaneasNuncaVendemMaisDoQueOSaldo() throws Exception {
        UUID casa = criarImovel("PDV Concorrente");
        UUID produto = criarProduto("PDV-CNC", "6.00", true);
        entrada(casa, produto, 10);
        String corpo = vendaJson(casa, item(produto, 1));

        List<Integer> respostas;
        try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
            List<CompletableFuture<Integer>> chamadas = IntStream.range(0, 30)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return vender("it-concorrente-" + i, corpo).andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor)).toList();
            respostas = chamadas.stream().map(CompletableFuture::join).toList();
        }

        assertThat(respostas).filteredOn(codigo -> codigo == 201).hasSize(10);
        assertThat(respostas).filteredOn(codigo -> codigo == 422).hasSize(20);
        assertThat(saldo(casa, produto)).isZero();
        assertThat(contar("movimentacao_estoque WHERE imovel_id = ? AND tipo = 'SAIDA_VENDA'", casa)).isEqualTo(10);
    }

    @Test
    void cancelamentoDevolveOEstoqueUmaUnicaVez() throws Exception {
        UUID casa = criarImovel("PDV Cancelamento");
        UUID produto = criarProduto("PDV-CANC", "6.00", true);
        entrada(casa, produto, 10);
        UUID vendaId = venderComSucesso(casa, item(produto, 4));
        assertThat(saldo(casa, produto)).isEqualTo(6);

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mockMvc.perform(post("/pdv/vendas/{id}/cancelar", vendaId).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motivo\": \"Lançada errada\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELADA"))
                    .andExpect(jsonPath("$.data.canceladoEm").exists());
        }

        assertThat(saldo(casa, produto)).isEqualTo(10);
        assertThat(contar("movimentacao_estoque WHERE referencia_id = ? AND tipo = 'DEVOLUCAO'", vendaId)).isEqualTo(1);
        mockMvc.perform(get("/pdv/vendas").param("imovelId", casa.toString()).param("status", "CANCELADA"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void devolucaoParcialDepoisCancelamentoDevolveSoOResto() throws Exception {
        UUID casa = criarImovel("PDV Devolucao");
        UUID produto = criarProduto("PDV-DEV", "6.00", true);
        UUID taxa = criarProduto("PDV-DEV-TAXA", "50.00", false);
        entrada(casa, produto, 10);
        UUID vendaId = venderComSucesso(casa, item(produto, 5) + "," + item(taxa, 1));
        String devolucao = "{\"motivo\": \"Cliente desistiu\", \"itens\": [%s]}";

        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", "it-devolucao-01")
                        .contentType(MediaType.APPLICATION_JSON).content(devolucao.formatted(item(produto, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDevolvido").value(12.00))
                .andExpect(jsonPath("$.data.pagamentoStatus").value("PENDENTE"));
        assertThat(saldo(casa, produto)).isEqualTo(7);
        // Repetição com a mesma chave não devolve de novo.
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", "it-devolucao-01")
                        .contentType(MediaType.APPLICATION_JSON).content(devolucao.formatted(item(produto, 2))))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
        assertThat(saldo(casa, produto)).isEqualTo(7);
        // Mais do que resta, e item sem estoque, são recusados.
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", "it-devolucao-02")
                        .contentType(MediaType.APPLICATION_JSON).content(devolucao.formatted(item(produto, 4))))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", "it-devolucao-03")
                        .contentType(MediaType.APPLICATION_JSON).content(devolucao.formatted(item(taxa, 1))))
                .andExpect(status().isUnprocessableContent());
        assertThat(saldo(casa, produto)).isEqualTo(7);

        mockMvc.perform(post("/pdv/vendas/{id}/cancelar", vendaId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cancelada\"}"))
                .andExpect(status().isOk());
        assertThat(saldo(casa, produto)).isEqualTo(10);
        mockMvc.perform(post("/pdv/vendas/{id}/devolucoes", vendaId).header("Idempotency-Key", "it-devolucao-04")
                        .contentType(MediaType.APPLICATION_JSON).content(devolucao.formatted(item(produto, 1))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void recusaProdutoInativoRepetidoEImovelInativo() throws Exception {
        UUID casa = criarImovel("PDV Regras");
        UUID produto = criarProduto("PDV-REGRA", "6.00", true);
        UUID inativo = criarProduto("PDV-REGRA-INATIVO", "6.00", true);
        entrada(casa, produto, 10);
        entrada(casa, inativo, 10);
        mockMvc.perform(patch("/produtos/{id}/inativar", inativo)).andExpect(status().isOk());

        vender("it-regra-inativo", vendaJson(casa, item(inativo, 1))).andExpect(status().isUnprocessableContent());
        vender("it-regra-repetido", vendaJson(casa, item(produto, 1) + "," + item(produto, 2)))
                .andExpect(status().isUnprocessableContent());
        vender("it-regra-inexistente", vendaJson(casa, item(UUID.randomUUID(), 1))).andExpect(status().isNotFound());
        vender("it-regra-imovel-404", vendaJson(UUID.randomUUID(), item(produto, 1))).andExpect(status().isNotFound());

        mockMvc.perform(patch("/imoveis/{id}/inativar", casa)).andExpect(status().isOk());
        vender("it-regra-imovel-inativo", vendaJson(casa, item(produto, 1)))
                .andExpect(status().isUnprocessableContent());
        assertThat(saldo(casa, produto)).isEqualTo(10);
    }
}
