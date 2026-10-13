package com.gestaocasas.integration;

import com.gestaocasas.ImagemFixtures;
import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.storage.StoragePort;
import com.gestaocasas.domain.storage.StorageUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe a aplicação inteira contra um PostgreSQL real: valida a migração Flyway, o mapeamento
 * JPA (ddl-auto=validate) e o comportamento das constraints. Exige Docker ou IT_DATABASE_URL
 * (veja {@link BancoDeTeste}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImovelPersistenceIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
        registry.add("app.fotos.escrita-habilitada", () -> "true");
    }

    private static final String URL_PUBLICA = "https://teste.supabase.co/storage/v1/object/public/imoveis/";

    // O Storage é sempre simulado: os testes nunca tocam o bucket real.
    @MockitoBean
    private StoragePort storage;

    @BeforeEach
    void storageAceitaUploads() {
        when(storage.upload(anyString(), any(), anyString())).thenAnswer(invocation ->
                new StorageUploadResult(invocation.getArgument(0), URL_PUBLICA + invocation.getArgument(0)));
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
        assertThat(migracoes).isEqualTo(7);
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

    private JsonNode enviar(UUID imovelId, byte[] conteudo, String contentType, String principal) throws Exception {
        var requisicao = multipart("/imoveis/{id}/fotos/upload", imovelId)
                .file(new MockMultipartFile("file", "foto qualquer.bin", contentType, conteudo));
        if (principal != null) {
            requisicao.param("principal", principal);
        }
        String body = mockMvc.perform(requisicao).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data");
    }

    @Test
    void uploadGravaReferenciaEListagemDevolveUrlPublica() throws Exception {
        UUID id = criar("Casa Com Upload", null);

        JsonNode primeira = enviar(id, ImagemFixtures.png(800, 600), "image/png", null);
        JsonNode segunda = enviar(id, ImagemFixtures.webp(800, 600), "image/webp", null);

        assertThat(primeira.at("/principal").asBoolean()).isTrue();
        assertThat(primeira.at("/ordem").asInt()).isZero();
        assertThat(segunda.at("/principal").asBoolean()).isFalse();
        assertThat(segunda.at("/ordem").asInt()).isEqualTo(1);

        String caminho = jdbc.queryForObject("SELECT storage_path FROM imovel_foto WHERE id = ?", String.class,
                UUID.fromString(primeira.at("/id").asString()));
        assertThat(caminho).matches(id + "/[0-9a-f-]{36}\\.png");
        assertThat(primeira.at("/url").asString()).isEqualTo(URL_PUBLICA + caminho);

        mockMvc.perform(get("/imoveis/{id}/fotos", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].url").value(URL_PUBLICA + caminho))
                .andExpect(jsonPath("$.data[0].principal").value(true));
        mockMvc.perform(get("/imoveis/{id}", id)).andExpect(jsonPath("$.data.fotos.length()").value(2));
    }

    @Test
    void uploadRejeitaArquivoInvalidoSemGravar() throws Exception {
        UUID id = criar("Casa Upload Invalido", null);

        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", id)
                        .file(new MockMultipartFile("file", "a.png", "image/png", "<html></html>".getBytes())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", id)
                        .file(new MockMultipartFile("file", "a.svg", "image/svg+xml", "<svg/>".getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/imoveis/{id}/fotos/upload", UUID.randomUUID())
                        .file(new MockMultipartFile("file", "a.png", "image/png", ImagemFixtures.png(1, 1))))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM imovel_foto WHERE imovel_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    void fotoPrincipalEUnicaETrocaEntreFotoLegadaEUpload() throws Exception {
        UUID id = criar("Casa Principal", null);
        String legada = mockMvc.perform(post("/imoveis/{id}/fotos", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://exemplo.com/legada.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.principal").value(false))
                .andReturn().getResponse().getContentAsString();
        String legadaId = objectMapper.readTree(legada).at("/data/id").asString();
        String uploadId = enviar(id, ImagemFixtures.jpeg(640, 480), "image/jpeg", "true").at("/id").asString();

        mockMvc.perform(patch("/imoveis/{id}/fotos/{fotoId}/principal", id, legadaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.principal").value(true));

        assertThat(jdbc.queryForList("SELECT id::text FROM imovel_foto WHERE imovel_id = ? AND principal",
                String.class, id)).containsExactly(legadaId);

        // Excluir a principal promove a próxima foto na ordem.
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", id, legadaId)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT id::text FROM imovel_foto WHERE imovel_id = ? AND principal",
                String.class, id)).containsExactly(uploadId);

        UUID outro = criar("Casa Sem Essa Foto", null);
        mockMvc.perform(patch("/imoveis/{id}/fotos/{fotoId}/principal", outro, uploadId))
                .andExpect(status().isNotFound());
    }

    @Test
    void exclusaoComStorageForaDoArFicaPendenteEPodeSerRepetida() throws Exception {
        UUID id = criar("Casa Exclusao Pendente", null);
        UUID fotoId = UUID.fromString(enviar(id, ImagemFixtures.png(10, 10), "image/png", null).at("/id").asString());
        String caminho = jdbc.queryForObject("SELECT storage_path FROM imovel_foto WHERE id = ?", String.class, fotoId);

        doThrow(new StorageException("fora do ar")).when(storage).delete(caminho);
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", id, fotoId)).andExpect(status().isBadGateway());

        // O registro continua no banco, com o caminho do objeto, mas some das consultas.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM imovel_foto WHERE id = ? AND exclusao_pendente_em IS NOT NULL AND NOT principal",
                Integer.class, fotoId)).isEqualTo(1);
        mockMvc.perform(get("/imoveis/{id}/fotos", id)).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/imoveis/{id}", id)).andExpect(jsonPath("$.data.fotos.length()").value(0));

        Mockito.doNothing().when(storage).delete(caminho);
        mockMvc.perform(delete("/imoveis/{id}/fotos/{fotoId}", id, fotoId)).andExpect(status().isNoContent());

        verify(storage, Mockito.times(2)).delete(caminho);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM imovel_foto WHERE id = ?", Integer.class, fotoId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM imovel WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void bancoGaranteRegrasDasFotos() throws Exception {
        UUID id = criar("Casa Regras De Foto", null);
        String insert = """
                INSERT INTO imovel_foto (id, imovel_id, url, ordem, criado_em, storage_provider, storage_path, principal)
                VALUES (gen_random_uuid(), ?, 'https://exemplo.com/x.jpg', 0, now(), ?, ?, ?)
                """;

        assertThat(jdbc.update(insert, id, "EXTERNA", null, true)).isEqualTo(1);
        // Segunda principal no mesmo imóvel
        assertThatThrownBy(() -> jdbc.update(insert, id, "EXTERNA", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Arquivo no Storage sem caminho, e URL externa com caminho
        assertThatThrownBy(() -> jdbc.update(insert, id, "SUPABASE", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, id, "EXTERNA", "a/b.png", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Foto de imóvel inexistente
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "EXTERNA", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Registro no formato anterior à V3 continua aceito
        assertThat(jdbc.update("""
                INSERT INTO imovel_foto (id, imovel_id, url, ordem, criado_em)
                VALUES (gen_random_uuid(), ?, 'https://exemplo.com/antiga.jpg', 1, now())
                """, id)).isEqualTo(1);
        mockMvc.perform(get("/imoveis/{id}/fotos", id)).andExpect(jsonPath("$.data.length()").value(2));
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
