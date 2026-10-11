package com.gestaocasas.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe a aplicação inteira contra um PostgreSQL real: valida a migração Flyway, o mapeamento
 * JPA (ddl-auto=validate) e o comportamento das constraints. Exige Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ImovelPersistenceIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        registry.add("DATABASE_USERNAME", POSTGRES::getUsername);
        registry.add("DATABASE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String imovelJson(String nome, String status) {
        return """
                {
                  "nome": "%s",
                  "descricao": "Descrição",
                  "logradouro": "Rua das Conchas",
                  "numero": "120",
                  "cidade": "Ubatuba",
                  "uf": "sp",
                  "cep": "11680-000",
                  "capacidadeMaxima": 8,
                  "quartos": 3,
                  "banheiros": 2,
                  "camas": 5,
                  "valorBaseDiaria": 450.50,
                  "horarioCheckin": "15:00",
                  "horarioCheckout": "11:00",
                  "status": %s,
                  "comodidades": ["Wi-Fi", "Piscina"]
                }
                """.formatted(nome, status == null ? "null" : "\"" + status + "\"");
    }

    private UUID criar(String nome, String status) throws Exception {
        String body = mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON)
                        .content(imovelJson(nome, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return UUID.fromString(json.at("/data/id").asString());
    }

    @Test
    void migracaoCriaTabelasDoModuloDeImoveis() {
        Integer tabelas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN ('imovel', 'imovel_comodidade', 'imovel_foto')
                """, Integer.class);
        assertThat(tabelas).isEqualTo(3);

        Integer migracoes = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(migracoes).isEqualTo(1);
    }

    @Test
    void persisteEConsultaImovelCompleto() throws Exception {
        UUID id = criar("Casa Persistida", null);

        mockMvc.perform(get("/imoveis/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Casa Persistida"))
                .andExpect(jsonPath("$.data.uf").value("SP"))
                .andExpect(jsonPath("$.data.status").value("ATIVO"))
                .andExpect(jsonPath("$.data.valorBaseDiaria").value(450.50))
                .andExpect(jsonPath("$.data.horarioCheckin").value("15:00:00"))
                .andExpect(jsonPath("$.data.comodidades.length()").value(2))
                .andExpect(jsonPath("$.data.criadoEm").exists());

        assertThat(jdbc.queryForObject("SELECT valor_base_diaria FROM imovel WHERE id = ?",
                java.math.BigDecimal.class, id)).isEqualByComparingTo("450.50");
    }

    @Test
    void atualizaImovelESubstituiComodidades() throws Exception {
        UUID id = criar("Casa Para Atualizar", null);

        mockMvc.perform(put("/imoveis/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(imovelJson("Casa Atualizada", "MANUTENCAO").replace("\"Piscina\"", "\"Sauna\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Casa Atualizada"))
                .andExpect(jsonPath("$.data.status").value("MANUTENCAO"));

        assertThat(jdbc.queryForList("SELECT comodidade FROM imovel_comodidade WHERE imovel_id = ?",
                String.class, id)).containsExactlyInAnyOrder("Wi-Fi", "Sauna");
    }

    @Test
    void inativarMantemRegistroNoBanco() throws Exception {
        UUID id = criar("Casa Para Inativar", null);

        mockMvc.perform(patch("/imoveis/{id}/inativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INATIVO"));

        assertThat(jdbc.queryForObject("SELECT status FROM imovel WHERE id = ?", String.class, id))
                .isEqualTo("INATIVO");
    }

    @Test
    void filtraPorStatusENomeComPaginacao() throws Exception {
        String marca = "Filtro" + UUID.randomUUID().toString().substring(0, 8);
        criar(marca + " Alfa", "ATIVO");
        criar(marca + " Beta", "ATIVO");
        criar(marca + " Gama", "INATIVO");

        mockMvc.perform(get("/imoveis").param("nome", marca.toUpperCase()).param("status", "ATIVO")
                        .param("size", "1").param("sort", "nome,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].nome").value(marca + " Beta"));
    }

    @Test
    void curingasDoLikeSaoTratadosComoTexto() throws Exception {
        criar("Casa Sem Curinga " + UUID.randomUUID(), null);

        mockMvc.perform(get("/imoveis").param("nome", "%_%_%_inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void ordenacaoPorCampoInexistenteRetorna400() throws Exception {
        mockMvc.perform(get("/imoveis").param("sort", "naoExiste")).andExpect(status().isBadRequest());
    }

    @Test
    void cadastraERemoveFotos() throws Exception {
        UUID id = criar("Casa Com Fotos", null);

        mockMvc.perform(post("/imoveis/{id}/fotos", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/1.jpg\", \"descricao\": \"Fachada\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ordem").value(0));
        String segunda = mockMvc.perform(post("/imoveis/{id}/fotos", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/2.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ordem").value(1))
                .andReturn().getResponse().getContentAsString();
        String fotoId = objectMapper.readTree(segunda).at("/data/id").asString();

        mockMvc.perform(get("/imoveis/{id}", id))
                .andExpect(jsonPath("$.data.fotos.length()").value(2))
                .andExpect(jsonPath("$.data.fotos[0].url").value("https://exemplo.com/1.jpg"));

        UUID outroImovel = criar("Outra Casa", null);
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", outroImovel, fotoId))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", id, fotoId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/imoveis/{id}", id)).andExpect(jsonPath("$.data.fotos.length()").value(1));
    }

    @Test
    void bancoRejeitaDadosQueViolamConstraints() {
        String insert = """
                INSERT INTO imovel (id, nome, logradouro, cidade, uf, capacidade_maxima, quartos, banheiros, camas,
                                    valor_base_diaria, horario_checkin, horario_checkout, status, criado_em, atualizado_em)
                VALUES (gen_random_uuid(), 'X', 'Rua', 'Cidade', ?, ?, 1, 1, 1, ?, '15:00', '11:00', ?, now(), now())
                """;

        assertThatThrownBy(() -> jdbc.update(insert, "SP", 0, 100, "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "SP", 4, -1, "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "SP", 4, 100, "VENDIDO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "sp", 4, 100, "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insert, "SP", 4, 100, "ATIVO")).isEqualTo(1);
    }

    @Test
    void healthCheckResponde() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
