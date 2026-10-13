package com.gestaocasas.integration;

import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Busca dinâmica contra um PostgreSQL real: migração V7 (normalização, colunas geradas e índices
 * trigram), acentos, maiúsculas, código, saldo por casa, prioridade e limite. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProdutoBuscaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
        registry.add("app.produtos.busca.limite-maximo", () -> "3");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private static UUID casaA;
    private static UUID casaB;
    private static UUID cocaZero;

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

    private UUID criarProduto(String codigo, String codigoBarras, String nome, String categoria, boolean estoque)
            throws Exception {
        return criar("/produtos", """
                {"codigo": "%s", "codigoBarras": %s, "nome": "%s", "categoria": "%s", "unidade": "UN",
                 "precoVenda": 6.00, "controlaEstoque": %s}
                """.formatted(codigo, codigoBarras == null ? "null" : "\"" + codigoBarras + "\"", nome, categoria,
                estoque));
    }

    private void entrada(UUID imovelId, UUID produtoId, int quantidade) throws Exception {
        mockMvc.perform(post("/imoveis/{id}/estoque/entradas", imovelId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\": \"%s\", \"quantidade\": %d}".formatted(produtoId, quantidade)))
                .andExpect(status().isCreated());
    }

    private ResultActions buscar(String termo, UUID imovelId) throws Exception {
        var request = get("/produtos/buscar").param("termo", termo);
        if (imovelId != null) {
            request.param("imovelId", imovelId.toString());
        }
        return mockMvc.perform(request).andExpect(status().isOk());
    }

    @BeforeEach
    void catalogo() throws Exception {
        if (casaA != null) {
            return;
        }
        casaA = criarImovel("Busca Casa A");
        casaB = criarImovel("Busca Casa B");
        UUID coca = criarProduto("COCA350", "7894900010015", "Coca-Cola Lata 350ml", "CONSUMO", true);
        cocaZero = criarProduto("COCAZ", "7894900700015", "Coca-Cola Zero 350ml", "CONSUMO", true);
        UUID agua = criarProduto("H2O", null, "Água Mineral 500ml", "CONSUMO", true);
        criarProduto("PAO", null, "Pão de Açúcar com Côco", "CONSUMO", true);
        criarProduto("LANCHA", null, "Passeio de Lancha", "PASSEIO", false);
        UUID inativo = criarProduto("COCAOLD", null, "Coca-Cola Garrafa Antiga", "CONSUMO", true);
        mockMvc.perform(patch("/produtos/{id}/inativar", inativo)).andExpect(status().isOk());
        entrada(casaA, coca, 20);
        entrada(casaB, coca, 8);
        entrada(casaB, cocaZero, 3);
        entrada(casaA, agua, 5);
    }

    @Test
    void migracaoCriaColunasGeradasEIndicesTrigram() {
        String normalizado = jdbc.queryForObject("SELECT normalizar_busca('ÁGUA Côco AÇAÍ Pão')", String.class);
        assertThat(normalizado).isEqualTo("agua coco acai pao");

        Integer indices = jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'produto' AND indexname LIKE 'idx_produto_%_trgm'
                """, Integer.class);
        assertThat(indices).isEqualTo(3);
    }

    @Test
    void encontraAPartirDeUmCaractere() throws Exception {
        buscar("c", casaA).andExpect(jsonPath("$.data[*].nome", hasItem("Coca-Cola Lata 350ml")));
    }

    @Test
    void encontraPorParteDoNomeSemDiferenciarMaiusculas() throws Exception {
        buscar("coca", casaA).andExpect(jsonPath("$.data[*].nome", hasItem("Coca-Cola Lata 350ml")));
        buscar("COLA", casaA).andExpect(jsonPath("$.data[*].nome", hasItem("Coca-Cola Lata 350ml")));
        buscar("zero", casaA).andExpect(jsonPath("$.data[*].nome", contains("Coca-Cola Zero 350ml")));
    }

    @Test
    void ignoraAcentosNosDoisSentidos() throws Exception {
        buscar("agua", casaA).andExpect(jsonPath("$.data[*].nome", contains("Água Mineral 500ml")));
        buscar("ÁGUA", casaA).andExpect(jsonPath("$.data[*].nome", contains("Água Mineral 500ml")));
        buscar("acucar com coco", casaA).andExpect(jsonPath("$.data[*].nome", contains("Pão de Açúcar com Côco")));
        buscar("láncha", casaA).andExpect(jsonPath("$.data[*].nome", contains("Passeio de Lancha")));
    }

    @Test
    void encontraPorCodigoEPorCodigoDeBarras() throws Exception {
        buscar("h2o", casaA).andExpect(jsonPath("$.data[*].nome", contains("Água Mineral 500ml")));
        buscar("7894900010015", casaA).andExpect(jsonPath("$.data[*].nome", contains("Coca-Cola Lata 350ml")));
        buscar("78949007", casaA).andExpect(jsonPath("$.data[*].nome", contains("Coca-Cola Zero 350ml")));
    }

    @Test
    void saldoVemExclusivamenteDaCasaPesquisada() throws Exception {
        buscar("coca-cola lata", casaA).andExpect(jsonPath("$.data[0].quantidadeDisponivel").value(20));
        buscar("coca-cola lata", casaB).andExpect(jsonPath("$.data[0].quantidadeDisponivel").value(8));
        // A Casa A não tem Coca Zero: o saldo 3 da Casa B não pode aparecer.
        buscar("zero", casaA)
                .andExpect(jsonPath("$.data[0].quantidadeDisponivel").value(0))
                .andExpect(jsonPath("$.data[0].semEstoque").value(true));
        buscar("zero", null)
                .andExpect(jsonPath("$.data[0].quantidadeDisponivel").doesNotExist())
                .andExpect(jsonPath("$.data[0].semEstoque").value(false));
    }

    @Test
    void priorizaProdutosDisponiveisNaCasa() throws Exception {
        buscar("coca", casaA).andExpect(jsonPath("$.data[*].nome",
                contains("Coca-Cola Lata 350ml", "Coca-Cola Zero 350ml")));
        // Produto sem controle de estoque conta como disponível.
        buscar("lancha", casaA)
                .andExpect(jsonPath("$.data[0].quantidadeDisponivel").doesNotExist())
                .andExpect(jsonPath("$.data[0].semEstoque").value(false));
    }

    @Test
    void inativosSoAparecemQuandoOFiltroEDesligado() throws Exception {
        buscar("antiga", casaA).andExpect(jsonPath("$.data", hasSize(0)));
        buscar("coca", casaA).andExpect(jsonPath("$.data[*].nome", not(hasItem("Coca-Cola Garrafa Antiga"))));
        mockMvc.perform(get("/produtos/buscar").param("termo", "antiga").param("somenteAtivos", "false"))
                .andExpect(jsonPath("$.data[*].nome", contains("Coca-Cola Garrafa Antiga")))
                .andExpect(jsonPath("$.data[0].ativo").value(false));
    }

    @Test
    void respeitaOLimitePedidoEOMaximoConfigurado() throws Exception {
        mockMvc.perform(get("/produtos/buscar").param("termo", "a").param("limit", "2"))
                .andExpect(jsonPath("$.data", hasSize(2)));
        // limite-maximo = 3 neste teste; há mais de 3 produtos com a letra "a".
        mockMvc.perform(get("/produtos/buscar").param("termo", "a").param("limit", "500"))
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void curingasDigitadosSaoTratadosComoTexto() throws Exception {
        buscar("%", casaA).andExpect(jsonPath("$.data", hasSize(0)));
        buscar("_", casaA).andExpect(jsonPath("$.data", hasSize(0)));
        buscar("c%a", casaA).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void imovelInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/produtos/buscar").param("termo", "c").param("imovelId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
