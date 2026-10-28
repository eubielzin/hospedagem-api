package com.gestaocasas.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Painel do dia (RF-55), pessoas do dia (RF-51 a RF-53) e configuração (RF-67). Banco próprio: contagens exatas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class OperacaoIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String criar(String url, String json) throws Exception {
        String corpo = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    private String uh(String nome, String tipo, String categoria) throws Exception {
        return criar("/imoveis", """
                {"nome": "%s", "tipo": "%s", "categoriaId": "%s", "logradouro": "Rua", "cidade": "Barreirinhas", "uf": "MA",
                 "capacidadeAdultos": 4, "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1, "camas": 2,
                 "valorBaseDiaria": 0}""".formatted(nome, tipo, categoria));
    }

    private String reserva(String uh, String hospede, String canal, LocalDate checkin, LocalDate checkout,
                           int adultos, int criancas) throws Exception {
        return criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": %d,
                 "criancas": %d, "regimePensao": "CAFE_DA_MANHA"}"""
                .formatted(uh, hospede, canal, checkin, checkout, adultos, criancas));
    }

    @Test
    void painelEPessoasDoDia() throws Exception {
        String categoria = criar("/categorias", "{\"nome\": \"Categoria Painel\", \"nivel\": 1}");
        criar("/tarifas", """
                {"categoriaId": "%s", "nome": "Base", "inicio": "%s", "fim": "%s", "valorDiaria": 300.00,
                 "estadiaMinima": 1, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoria, HOJE.minusDays(10), HOJE.plusDays(60)));
        String alegria = uh("Casa Alegria", "CASA", categoria);
        String flor = uh("Casa Flor", "CASA", categoria);
        String completo = uh("Imóvel completo", "IMOVEL_COMPLETO", categoria);
        String ana = criar("/hospedes", """
                {"nome": "Ana Lima", "tipoDocumento": "CPF", "numeroDocumento": "52998224725", "nacionalidade": "Brasileira",
                 "observacoesAlimentares": "Vegetariana"}""");
        String canal = jdbc.queryForObject("SELECT id::text FROM canal WHERE nome = 'Direto (recepção)'", String.class);

        String hospedada = reserva(alegria, ana, canal, HOJE, HOJE.plusDays(2), 2, 1);
        mockMvc.perform(put("/reservas/{id}/fnrh", hospedada).contentType(MediaType.APPLICATION_JSON).content("""
                {"cidadeProcedencia": "Belém/PA", "cidadeDestino": "Barreirinhas/MA", "motivoViagem": "LAZER",
                 "meioTransporte": "CARRO"}""")).andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/checkin", hospedada)).andExpect(status().isOk());
        String chegaAmanha = reserva(flor, ana, canal, HOJE.plusDays(1), HOJE.plusDays(3), 1, 0);
        String saiHoje = reserva(flor, ana, canal, HOJE.minusDays(2), HOJE, 1, 0);
        String cancelada = reserva(flor, ana, canal, HOJE.plusDays(3), HOJE.plusDays(4), 1, 0);
        mockMvc.perform(post("/reservas/{id}/cancelamento", cancelada).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Desistiu\"}")).andExpect(status().isOk());
        String noCompleto = reserva(completo, ana, canal, HOJE.plusDays(20), HOJE.plusDays(22), 3, 1);

        mockMvc.perform(get("/painel/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").value(HOJE.toString()))
                .andExpect(jsonPath("$.data.totalUhs").value(2))
                .andExpect(jsonPath("$.data.uhsOcupadas").value(1))
                .andExpect(jsonPath("$.data.ocupacao").value(0.5))
                .andExpect(jsonPath("$.data.adultosNaCasa").value(2))
                .andExpect(jsonPath("$.data.criancasNaCasa").value(1))
                .andExpect(jsonPath("$.data.diariasPrevistas").value(300.00))
                .andExpect(jsonPath("$.data.adr").value(300.00))
                .andExpect(jsonPath("$.data.revpar").value(150.00))
                .andExpect(jsonPath("$.data.chegadasHoje[*].id", contains(hospedada)))
                .andExpect(jsonPath("$.data.saidasHoje[*].id", contains(saiHoje)))
                .andExpect(jsonPath("$.data.chegadasAmanha[*].id", contains(chegaAmanha)))
                .andExpect(jsonPath("$.data.saidasAmanha").isEmpty())
                .andExpect(jsonPath("$.data.chegadasHoje[0].uhNome").value("Casa Alegria"));
        // Imóvel completo reservado: todas as casas ocupadas.
        mockMvc.perform(get("/painel/resumo").param("data", HOJE.plusDays(21).toString()))
                .andExpect(jsonPath("$.data.uhsOcupadas").value(2))
                .andExpect(jsonPath("$.data.ocupacao").value(1.0))
                .andExpect(jsonPath("$.data.adultosNaCasa").value(0))
                .andExpect(jsonPath("$.data.saidasAmanha[*].id", contains(noCompleto)));

        mockMvc.perform(get("/pessoas-do-dia").param("data", HOJE.toString()))
                .andExpect(jsonPath("$.data.linhas[*].reservaId", contains(hospedada)))
                .andExpect(jsonPath("$.data.linhas[0].titularNome").value("Ana Lima"))
                .andExpect(jsonPath("$.data.linhas[0].regimePensao").value("Café da manhã"))
                .andExpect(jsonPath("$.data.linhas[0].observacoesAlimentares").value("Vegetariana"))
                .andExpect(jsonPath("$.data.totalAdultos").value(2))
                .andExpect(jsonPath("$.data.totalCriancas").value(1));
        // Quem sai no dia não aparece.
        mockMvc.perform(get("/pessoas-do-dia").param("data", HOJE.plusDays(2).toString()))
                .andExpect(jsonPath("$.data.linhas").isEmpty());
        mockMvc.perform(get("/pessoas-do-dia").param("data", "ontem")).andExpect(status().isBadRequest());
    }

    @Test
    void configuracaoDaPousada() throws Exception {
        mockMvc.perform(get("/configuracao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nomePousada").value("Pousada Casas Marias"))
                .andExpect(jsonPath("$.data.horarioCheckin").value("14:00"))
                .andExpect(jsonPath("$.data.idadeLimiteCrianca").value(12))
                .andExpect(jsonPath("$.data.formasPagamento", contains("PIX", "CREDITO", "DEBITO", "DINHEIRO", "OUTRO")));
        String nova = """
                {"nomePousada": "Pousada Casas Marias", "cidade": "Barreirinhas/MA", "horarioCheckin": "15:30",
                 "horarioCheckout": "11:00", "idadeLimiteCrianca": 10, "politicaCancelamento": "Multa de 30%%",
                 "formasPagamento": ["DINHEIRO", "PIX", "PIX"]}""".formatted();
        mockMvc.perform(put("/configuracao").contentType(MediaType.APPLICATION_JSON).content(nova))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horarioCheckin").value("15:30"))
                .andExpect(jsonPath("$.data.formasPagamento", contains("DINHEIRO", "PIX")));
        mockMvc.perform(put("/configuracao").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nomePousada": " ", "cidade": "X", "horarioCheckin": "15:30", "horarioCheckout": "11:00",
                         "idadeLimiteCrianca": 20, "politicaCancelamento": "X", "formasPagamento": []}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("nomePousada",
                        "idadeLimiteCrianca", "formasPagamento")));
        // Imóvel sem horário próprio passa a mostrar o novo padrão.
        String imovel = criar("/imoveis", """
                {"nome": "Casa Horário", "logradouro": "Rua", "cidade": "Barreirinhas", "uf": "MA", "capacidadeAdultos": 2,
                 "quartos": 1, "banheiros": 1, "camas": 1, "valorBaseDiaria": 0}""");
        mockMvc.perform(get("/imoveis/{id}", imovel)).andExpect(jsonPath("$.data.horarioCheckin").value("15:30:00"));
    }
}
