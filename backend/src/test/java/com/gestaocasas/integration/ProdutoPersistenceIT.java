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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro de produtos contra um PostgreSQL real: migração V4, mapeamento JPA (ddl-auto=validate),
 * índices únicos e constraints. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProdutoPersistenceIT {

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

    private static String produtoJson(String codigo, String codigoBarras, String nome, String categoria) {
        return """
                {
                  "codigo": "%s",
                  "codigoBarras": %s,
                  "nome": "%s",
                  "categoria": "%s",
                  "unidade": "un",
                  "precoCusto": 3.20,
                  "precoVenda": 6.50,
                  "controlaEstoque": true
                }
                """.formatted(codigo, codigoBarras == null ? "null" : "\"" + codigoBarras + "\"", nome, categoria);
    }

    private UUID criar(String codigo, String codigoBarras, String nome, String categoria) throws Exception {
        String body = mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson(codigo, codigoBarras, nome, categoria)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).at("/data/id").asString());
    }

    @Test
    void migracaoCriaTabelaDeProdutosSemColunaDeQuantidade() {
        Integer colunasDeSaldo = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'produto'
                  AND column_name LIKE '%quantidade%'
                """, Integer.class);
        assertThat(colunasDeSaldo).isZero();

        Boolean rls = jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE oid = 'produto'::regclass", Boolean.class);
        assertThat(rls).isTrue();
    }

    @Test
    void persisteEConsultaProduto() throws Exception {
        UUID id = criar("IT-PERSISTE", "7890000000011", "Coca-Cola Lata 350ml", "CONSUMO");

        mockMvc.perform(get("/produtos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.codigo").value("IT-PERSISTE"))
                .andExpect(jsonPath("$.data.unidade").value("UN"))
                .andExpect(jsonPath("$.data.precoVenda").value(6.50))
                .andExpect(jsonPath("$.data.precoCusto").value(3.20))
                .andExpect(jsonPath("$.data.ativo").value(true))
                .andExpect(jsonPath("$.data.criadoEm").exists());
    }

    @Test
    void codigoEhUnicoIgnorandoMaiusculas() throws Exception {
        criar("IT-Dup", null, "Produto A", "CONSUMO");

        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson("it-dup", null, "Produto B", "CONSUMO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um produto com o código 'it-dup'"));
    }

    @Test
    void codigoDeBarrasEhUnicoMasOpcional() throws Exception {
        criar("IT-CB-1", "7890000000028", "Com barras", "CONSUMO");
        criar("IT-CB-2", null, "Sem barras 1", "SERVICO");
        criar("IT-CB-3", null, "Sem barras 2", "SERVICO");

        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson("IT-CB-4", "7890000000028", "Repetido", "CONSUMO")))
                .andExpect(status().isConflict());
    }

    @Test
    void atualizarMantendoOProprioCodigoNaoConflita() throws Exception {
        UUID id = criar("IT-UPD", "7890000000035", "Antes", "CONSUMO");

        mockMvc.perform(put("/produtos/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(produtoJson("IT-UPD", "7890000000035", "Depois", "TAXA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Depois"))
                .andExpect(jsonPath("$.data.categoria").value("TAXA"));
    }

    @Test
    void inativaEFiltraPorSituacaoCategoriaENome() throws Exception {
        UUID passeio = criar("IT-FLT-1", null, "Passeio de Lancha Filtro", "PASSEIO");
        criar("IT-FLT-2", null, "Taxa de Limpeza Filtro", "TAXA");

        mockMvc.perform(patch("/produtos/{id}/inativar", passeio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));

        mockMvc.perform(get("/produtos").param("nome", "FILTRO").param("ativo", "true"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].codigo").value("IT-FLT-2"));
        mockMvc.perform(get("/produtos").param("nome", "filtro").param("categoria", "PASSEIO"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].ativo").value(false));
        mockMvc.perform(get("/produtos").param("nome", "filtro"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].nome").value("Passeio de Lancha Filtro"));
    }

    @Test
    void bancoRecusaCategoriaEPrecoInvalidos() {
        String insert = """
                INSERT INTO produto (id, codigo, nome, categoria, unidade, preco_venda, controla_estoque, ativo,
                                     criado_em, atualizado_em)
                VALUES (gen_random_uuid(), ?, 'X', ?, 'UN', ?, true, true, now(), now())
                """;
        assertThatThrownBy(() -> jdbc.update(insert, "IT-CK-1", "BEBIDA", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "IT-CK-2", "CONSUMO", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
