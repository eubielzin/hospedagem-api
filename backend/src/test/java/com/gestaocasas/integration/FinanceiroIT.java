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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Financeiro (RF-44 a RF-50) num banco próprio, com os valores conferidos à mão. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class FinanceiroIT {

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
        String corpo = enviar(post(url), json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return mockMvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String hospedar(String uh, String hospede, String canal, LocalDate checkout) throws Exception {
        String reserva = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": 2}"""
                .formatted(uh, hospede, canal, HOJE, checkout));
        enviar(put("/reservas/{id}/fnrh", reserva), """
                {"cidadeProcedencia": "Fortaleza/CE", "cidadeDestino": "Barreirinhas/MA", "motivoViagem": "LAZER",
                 "meioTransporte": "CARRO"}""").andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/checkin", reserva)).andExpect(status().isOk());
        return reserva;
    }

    @Test
    void recebimentosRepassesPendenciasDespesasEFluxo() throws Exception {
        String categoria = criar("/categorias", "{\"nome\": \"Categoria Financeiro\", \"nivel\": 1}");
        criar("/tarifas", """
                {"categoriaId": "%s", "nome": "Base", "inicio": "%s", "fim": "%s", "valorDiaria": 500.00,
                 "estadiaMinima": 1, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoria, HOJE.minusDays(10), HOJE.plusDays(60)));
        String[] casas = new String[2];
        for (int i = 0; i < 2; i++) {
            casas[i] = criar("/imoveis", """
                    {"nome": "Casa F%d", "tipo": "CASA", "categoriaId": "%s", "logradouro": "Rua", "cidade": "Barreirinhas",
                     "uf": "MA", "capacidadeAdultos": 4, "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1,
                     "camas": 2, "valorBaseDiaria": 0}""".formatted(i, categoria));
        }
        String hospede = criar("/hospedes", """
                {"nome": "Bruno Sales", "tipoDocumento": "CPF", "numeroDocumento": "11144477735", "nacionalidade": "Brasileira"}""");
        String booking = jdbc.queryForObject("SELECT id::text FROM canal WHERE nome = 'Booking.com'", String.class);
        String direto = jdbc.queryForObject("SELECT id::text FROM canal WHERE nome = 'Direto (recepção)'", String.class);
        enviar(put("/canais/{id}", booking), "{\"nome\": \"Booking.com\", \"tipo\": \"OTA\", \"comissao\": 15}")
                .andExpect(status().isOk());

        // OTA: diárias de 1000 viram repasse (150 de comissão); o consumo de 50 fica com o hóspede.
        String ota = hospedar(casas[0], hospede, booking, HOJE.plusDays(2));
        enviar(post("/reservas/{id}/lancamentos", ota), "{\"descricao\": \"Frigobar\", \"quantidade\": 1, "
                + "\"valorUnitario\": 50}").andExpect(status().isCreated());
        // Direto: 500 de diária, 300 pagos.
        String direta = hospedar(casas[1], hospede, direto, HOJE.plusDays(1));
        enviar(post("/reservas/{id}/pagamentos", direta), "{\"forma\": \"PIX\", \"valorBruto\": 300}")
                .andExpect(status().isCreated());
        // Cancelada com 100 retidos (RN-08: só o retido é receita).
        String cancelada = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": 2}"""
                .formatted(casas[1], hospede, direto, HOJE.plusDays(10), HOJE.plusDays(12)));
        enviar(post("/reservas/{id}/cancelamento", cancelada), """
                {"motivo": "Desistiu", "multa": 100, "valorRetido": 100, "valorDevolver": 0}""").andExpect(status().isOk());
        // Venda avulsa paga: entra nos recebimentos sem reserva.
        String suco = criar("/produtos", """
                {"codigo": "SUCO", "nome": "Suco", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": 8.00,
                 "controlaEstoque": false}""");
        String venda = JsonPath.read(mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", "venda-financeiro-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelId\": \"%s\", \"itens\": [{\"produtoId\": \"%s\", \"quantidade\": 2}]}"
                                .formatted(casas[0], suco)))
                .andReturn().getResponse().getContentAsString(), "$.data.id");
        enviar(post("/pdv/vendas/{id}/pagamento", venda).header("Idempotency-Key", "pagamento-financeiro-1"),
                "{\"formaPagamento\": \"DEBITO\", \"taxa\": 0.50}").andExpect(status().isOk());

        mockMvc.perform(get("/financeiro/recebimentos").param("inicio", HOJE.toString()).param("fim", HOJE.toString()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.vendaId == '%s')].reservaId".formatted(venda), contains((Object) null)))
                .andExpect(jsonPath("$.data[?(@.vendaId == '%s')].valorLiquido".formatted(venda), contains(15.5)));

        mockMvc.perform(get("/financeiro/repasses"))
                .andExpect(jsonPath("$.data[*].reservaId", contains(ota)))
                .andExpect(jsonPath("$.data[0].canalNome").value("Booking.com"))
                .andExpect(jsonPath("$.data[0].valorBruto").value(1000.00))
                .andExpect(jsonPath("$.data[0].comissao").value(150.00))
                .andExpect(jsonPath("$.data[0].valorLiquido").value(850.00))
                .andExpect(jsonPath("$.data[0].previsao").value(HOJE.plusDays(2).toString()))
                .andExpect(jsonPath("$.data[0].recebidoEm").isEmpty());

        mockMvc.perform(get("/financeiro/pendencias"))
                .andExpect(jsonPath("$.data[*].reservaId", contains(direta, ota)))
                .andExpect(jsonPath("$.data[*].saldo", contains(200.00, 50.00)))
                .andExpect(jsonPath("$.data[0].status").value("Hospedado"))
                .andExpect(jsonPath("$.data[0].titularNome").value("Bruno Sales"))
                .andExpect(jsonPath("$.data[0].uhNome").value("Casa F1"));

        String aPagar = criar("/despesas", """
                {"categoria": "Energia", "fornecedor": "Equatorial", "valor": 300.00, "vencimento": "%s"}"""
                .formatted(HOJE.plusDays(5)));
        String paga = criar("/despesas", """
                {"categoria": "Limpeza", "fornecedor": "Mercado Central", "descricao": " ", "valor": 200.00,
                 "vencimento": "%s"}""".formatted(HOJE.minusDays(1)));
        enviar(patch("/despesas/{id}/pagamento", paga), "{\"data\": \"%s\", \"comprovante\": \"NF 123\"}".formatted(HOJE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPagamento").value(HOJE.toString()))
                .andExpect(jsonPath("$.data.descricao").isEmpty());
        enviar(patch("/despesas/{id}/pagamento", paga), "{\"data\": \"%s\"}".formatted(HOJE))
                .andExpect(status().isUnprocessableContent());
        enviar(post("/despesas"), "{\"categoria\": \"\", \"fornecedor\": \"X\", \"valor\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("categoria", "valor", "vencimento")));
        mockMvc.perform(get("/despesas"))
                .andExpect(jsonPath("$.data[*].id", contains(paga, aPagar)));
        mockMvc.perform(patch("/despesas/{id}/pagamento", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\": \"%s\"}".formatted(HOJE))).andExpect(status().isNotFound());

        enviar(patch("/financeiro/repasses/{id}/recebimento", ota), "{\"data\": \"%s\"}".formatted(HOJE.plusDays(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recebidoEm").value(HOJE.plusDays(1).toString()));
        enviar(patch("/financeiro/repasses/{id}/recebimento", ota), "{\"data\": \"%s\"}".formatted(HOJE))
                .andExpect(status().isUnprocessableContent());
        enviar(patch("/financeiro/repasses/{id}/recebimento", direta), "{\"data\": \"%s\"}".formatted(HOJE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-09"));

        // Realizado: 300 (PIX) + 15,50 (venda, líquido) + 850 (repasse) + 100 (retido) − 200 (despesa paga).
        // Previsto: − 300 (despesa a vencer).
        mockMvc.perform(get("/financeiro/fluxo").param("inicio", HOJE.minusDays(1).toString())
                        .param("fim", HOJE.plusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entradasRealizadas").value(1265.50))
                .andExpect(jsonPath("$.data.saidasRealizadas").value(200.00))
                .andExpect(jsonPath("$.data.entradasPrevistas").value(0))
                .andExpect(jsonPath("$.data.saidasPrevistas").value(300.00))
                .andExpect(jsonPath("$.data.saldoRealizado").value(1065.50))
                .andExpect(jsonPath("$.data.saldoPrevisto").value(765.50))
                .andExpect(jsonPath("$.data.linhas.length()").value(6))
                .andExpect(jsonPath("$.data.linhas[?(@.descricao =~ /Valor retido.*/)].valor", contains(100.00)))
                .andExpect(jsonPath("$.data.linhas[?(@.descricao =~ /Repasse Booking.*/)].realizado", contains(true)));
        mockMvc.perform(get("/financeiro/fluxo").param("inicio", HOJE.toString()).param("fim", HOJE.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }
}
