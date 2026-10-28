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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Indicadores e previsão contra o PostgreSQL real (as fórmulas estão em CalculadoraIndicadoresTest). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class IndicadoresIT {

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

    @Test
    void indicadoresEPrevisao() throws Exception {
        String categoria = criar("/categorias", "{\"nome\": \"Categoria Indicadores\", \"nivel\": 1}");
        criar("/tarifas", """
                {"categoriaId": "%s", "nome": "Base", "inicio": "%s", "fim": "%s", "valorDiaria": 400.00,
                 "estadiaMinima": 1, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoria, HOJE.minusDays(10), HOJE.plusDays(60)));
        String casa = criar("/imoveis", """
                {"nome": "Casa Única", "tipo": "CASA", "categoriaId": "%s", "logradouro": "Rua", "cidade": "Barreirinhas",
                 "uf": "MA", "capacidadeAdultos": 4, "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1, "camas": 2,
                 "valorBaseDiaria": 0}""".formatted(categoria));
        String hospede = criar("/hospedes", """
                {"nome": "Carla Mendes", "tipoDocumento": "CPF", "numeroDocumento": "52998224725",
                 "nacionalidade": "Brasileira", "endereco": "Parnaíba/PI"}""");
        String direto = jdbc.queryForObject("SELECT id::text FROM canal WHERE nome = 'Direto (recepção)'", String.class);
        String reserva = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": 2,
                 "criancas": 1, "regimePensao": "MEIA_PENSAO"}"""
                .formatted(casa, hospede, direto, HOJE, HOJE.plusDays(4)));
        mockMvc.perform(post("/reservas/{id}/pagamentos", reserva).contentType(MediaType.APPLICATION_JSON)
                .content("{\"forma\": \"CREDITO\", \"valorBruto\": 1600, \"taxa\": 48}")).andExpect(status().isCreated());
        criar("/despesas", """
                {"categoria": "Lavanderia", "fornecedor": "Lava Bem", "valor": 120.00, "vencimento": "%s"}"""
                .formatted(HOJE.plusDays(1)));

        // 4 noites de 400 numa casa só, em 10 dias.
        mockMvc.perform(get("/indicadores").param("inicio", HOJE.toString()).param("fim", HOJE.plusDays(9).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumo.noitesVendidas").value(4))
                .andExpect(jsonPath("$.data.resumo.ocupacao").value(0.4))
                .andExpect(jsonPath("$.data.resumo.receitaHospedagem").value(1600.00))
                .andExpect(jsonPath("$.data.resumo.adr").value(400.00))
                .andExpect(jsonPath("$.data.resumo.taxasCartao").value(48.00))
                .andExpect(jsonPath("$.data.resumo.despesas").value(120.00))
                .andExpect(jsonPath("$.data.resumo.resultado").value(1432.00))
                .andExpect(jsonPath("$.data.resumo.reservas").value(1))
                .andExpect(jsonPath("$.data.resumo.hospedes").value(3))
                .andExpect(jsonPath("$.data.anterior.noitesVendidas").value(0))
                .andExpect(jsonPath("$.data.ocupacaoDiaria", hasSize(10)))
                .andExpect(jsonPath("$.data.uhs[0].nome").value("Casa Única"))
                .andExpect(jsonPath("$.data.regimes[0].rotulo").value("Meia pensão"))
                .andExpect(jsonPath("$.data.origemEstados[0].rotulo").value("PI"))
                .andExpect(jsonPath("$.data.composicao[0].rotulo").value("Família com crianças"))
                .andExpect(jsonPath("$.data.formasPagamento[0].rotulo").value("Cartão de crédito"))
                .andExpect(jsonPath("$.data.canais[0].nome").value("Direto (recepção)"))
                .andExpect(jsonPath("$.data.clientes[*].nome", contains("Carla Mendes")));

        mockMvc.perform(get("/indicadores/previsao").param("inicio", HOJE.toString()).param("dias", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dias", hasSize(14)))
                .andExpect(jsonPath("$.data.semanas", hasSize(2)))
                .andExpect(jsonPath("$.data.noitesVendidas").value(4))
                .andExpect(jsonPath("$.data.receita").value(1600.00))
                .andExpect(jsonPath("$.data.dias[0].chegadas").value(1))
                .andExpect(jsonPath("$.data.vendidas7").value(1));

        mockMvc.perform(get("/indicadores").param("inicio", HOJE.toString()).param("fim", HOJE.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/indicadores").param("inicio", HOJE.toString()).param("fim", HOJE.plusYears(3).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/indicadores/previsao").param("inicio", HOJE.toString()).param("dias", "0"))
                .andExpect(status().isBadRequest());
    }
}
