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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Conta do hóspede, pagamentos, check-out com saldo (RN-05), comissão do canal e pagamento de venda do PDV. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class ContaPagamentoIT {

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

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String json) throws Exception {
        return mockMvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Casa com diária de 500,00 e um hóspede com documento: pronta para reservar e fazer check-in. */
    private String[] casaEHospede(String nome) throws Exception {
        String categoria = criar("/categorias", "{\"nome\": \"Categoria %s\", \"nivel\": 1}".formatted(nome));
        criar("/tarifas", """
                {"categoriaId": "%s", "nome": "Base", "inicio": "%s", "fim": "%s", "valorDiaria": 500.00,
                 "estadiaMinima": 1, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoria, HOJE.minusDays(5), HOJE.plusDays(60)));
        String casa = criar("/imoveis", """
                {"nome": "%s", "tipo": "CASA", "categoriaId": "%s", "logradouro": "Rua", "cidade": "Barreirinhas",
                 "uf": "MA", "capacidadeAdultos": 4, "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1,
                 "camas": 2, "valorBaseDiaria": 0}""".formatted(nome, categoria));
        String hospede = criar("/hospedes", """
                {"nome": "Titular %s", "tipoDocumento": "PASSAPORTE", "numeroDocumento": "P%s",
                 "nacionalidade": "Brasileira"}""".formatted(nome, Math.abs(nome.hashCode())));
        return new String[]{casa, hospede};
    }

    private String hospedar(String casa, String hospede, String canal) throws Exception {
        String reserva = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": 2}"""
                .formatted(casa, hospede, canal, HOJE, HOJE.plusDays(2)));
        enviar(put("/reservas/{id}/fnrh", reserva), """
                {"cidadeProcedencia": "Teresina/PI", "cidadeDestino": "Barreirinhas/MA", "motivoViagem": "LAZER",
                 "meioTransporte": "ONIBUS"}""").andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/checkin", reserva)).andExpect(status().isOk());
        return reserva;
    }

    @Test
    void canalGanhaComissaoECor() throws Exception {
        String canal = criar("/canais", "{\"nome\": \"Hotels.com\", \"tipo\": \"OTA\", \"comissao\": 18, \"cor\": \"#C2410C\"}");
        mockMvc.perform(get("/canais"))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].cor".formatted(canal), contains("#c2410c")))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].comissao".formatted(canal), contains(18.0)))
                .andExpect(jsonPath("$.data[?(@.nome == 'Booking.com')].cor", contains("#1d4ed8")));
        // Sem comissão nem cor na edição: ficam como estavam.
        enviar(put("/canais/{id}", canal), "{\"nome\": \"Hotels.com\", \"tipo\": \"OTA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comissao").value(18.0));
        enviar(post("/canais"), "{\"nome\": \"X\", \"tipo\": \"OTA\", \"comissao\": 101, \"cor\": \"vermelho\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("comissao", "cor")));
    }

    @Test
    void contaComLancamentosEstornoPagamentosECheckoutComSaldo() throws Exception {
        String[] dados = casaEHospede("Casa Conta");
        String casa = dados[0];
        String direto = jdbc.queryForObject("SELECT id::text FROM canal WHERE nome = 'Direto (recepção)'", String.class);
        String agua = criar("/produtos", """
                {"codigo": "AGUA500", "nome": "Água 500ml", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": 5.00,
                 "controlaEstoque": true}""");
        enviar(post("/imoveis/{id}/estoque/entradas", casa), "{\"produtoId\": \"%s\", \"quantidade\": 10}".formatted(agua))
                .andExpect(status().isCreated());

        String reservaFutura = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s", "adultos": 2}"""
                .formatted(casa, dados[1], direto, HOJE.plusDays(20), HOJE.plusDays(21)));
        enviar(post("/reservas/{id}/lancamentos", reservaFutura), "{\"descricao\": \"Passeio\", \"quantidade\": 1, "
                + "\"valorUnitario\": 100}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-06"));

        String reserva = hospedar(casa, dados[1], direto);
        // Produto: o preço é o do cadastro, não o enviado, e o estoque do imóvel baixa.
        enviar(post("/reservas/{id}/lancamentos", reserva), """
                {"produtoId": "%s", "quantidade": 3, "valorUnitario": 0.01}""".formatted(agua))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lancamentos[0].descricao").value("Água 500ml"))
                .andExpect(jsonPath("$.data.lancamentos[0].total").value(15.00))
                .andExpect(jsonPath("$.data.consumos").value(15.00));
        assertThat(saldoEstoque(casa, agua)).isEqualTo(7);
        String corpo = enviar(post("/reservas/{id}/lancamentos", reserva), """
                {"descricao": "Passeio de quadriciclo", "quantidade": 2, "valorUnitario": 150.00}""")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String passeio = JsonPath.read(corpo, "$.data.lancamentos[1].id");
        enviar(post("/reservas/{id}/lancamentos", reserva), "{\"quantidade\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("descricao"));

        // Estorno: sai dos consumos; o de produto devolve ao estoque.
        String lancamentoAgua = JsonPath.read(corpo, "$.data.lancamentos[0].id");
        enviar(post("/reservas/{id}/lancamentos/{l}/estorno", reserva, lancamentoAgua), "{\"motivo\": \"Lançada errada\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lancamentos[0].estornado").value(true))
                .andExpect(jsonPath("$.data.lancamentos[0].motivoEstorno").value("Lançada errada"))
                .andExpect(jsonPath("$.data.consumos").value(300.00));
        assertThat(saldoEstoque(casa, agua)).isEqualTo(10);
        enviar(post("/reservas/{id}/lancamentos/{l}/estorno", reserva, lancamentoAgua), "{\"motivo\": \"De novo\"}")
                .andExpect(status().isUnprocessableContent());
        enviar(post("/reservas/{id}/lancamentos/{l}/estorno", reservaFutura, passeio), "{\"motivo\": \"Outra conta\"}")
                .andExpect(status().isNotFound());

        // Conta: 1000 de diárias (direto) + 300 de consumo.
        enviar(post("/reservas/{id}/pagamentos", reserva), """
                {"forma": "CREDITO", "valorBruto": 800.00, "taxa": 24.00}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diarias").value(1000.00))
                .andExpect(jsonPath("$.data.repasseCanal").value(0))
                .andExpect(jsonPath("$.data.pagamentos").value(800.00))
                .andExpect(jsonPath("$.data.saldo").value(500.00))
                .andExpect(jsonPath("$.data.recebimentos[0].valorLiquido").value(776.00))
                .andExpect(jsonPath("$.data.recebimentos[0].data").value(HOJE.toString()))
                .andExpect(jsonPath("$.data.recebimentos[0].descricao").value(
                        org.hamcrest.Matchers.startsWith("Recebimento da reserva CM-")));
        enviar(post("/reservas/{id}/pagamentos", reserva), "{\"forma\": \"PIX\", \"valorBruto\": 10, \"taxa\": 11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("taxa"));

        // RN-05: com saldo, o check-out só sai autorizado.
        mockMvc.perform(post("/reservas/{id}/checkout", reserva))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-05"))
                .andExpect(jsonPath("$.message").value("A conta tem saldo de R$ 500,00. Quite ou registre a pendência "
                        + "autorizada pelo gerente."));
        enviar(post("/reservas/{id}/checkout", reserva), "{\"autorizarPendencia\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendenciaAutorizada").value(true));
        // Depois do check-out ainda recebe (quitar a pendência).
        enviar(post("/reservas/{id}/pagamentos", reserva), "{\"forma\": \"PIX\", \"valorBruto\": 500.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.saldo").value(0));

        enviar(post("/reservas/{id}/cancelamento", reservaFutura), "{\"motivo\": \"Desistiu\"}").andExpect(status().isOk());
        enviar(post("/reservas/{id}/pagamentos", reservaFutura), "{\"forma\": \"PIX\", \"valorBruto\": 50}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-03"));
        mockMvc.perform(get("/reservas/{id}", reserva))
                .andExpect(jsonPath("$.data.historico[*].descricao", org.hamcrest.Matchers.hasItems(
                        "Lançamento: 3× Água 500ml", "Estorno de Água 500ml: Lançada errada",
                        "Recebimento de R$ 800,00 (Cartão de crédito)")));
    }

    @Test
    void reservaDeOtaTemAsDiariasComoRepasseECopiaAComissao() throws Exception {
        String[] dados = casaEHospede("Casa OTA");
        String ota = criar("/canais", "{\"nome\": \"OTA Teste\", \"tipo\": \"OTA\", \"comissao\": 15}");
        String reserva = hospedar(dados[0], dados[1], ota);
        mockMvc.perform(get("/reservas/{id}/conta", reserva))
                .andExpect(jsonPath("$.data.diarias").value(1000.00))
                .andExpect(jsonPath("$.data.repasseCanal").value(1000.00))
                .andExpect(jsonPath("$.data.saldo").value(0));
        mockMvc.perform(post("/reservas/{id}/checkout", reserva)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT comissao_canal FROM reserva WHERE id = ?::uuid", String.class, reserva))
                .isEqualTo("15.00");
    }

    @Test
    void pagamentoDeVendaAvulsaComTroco() throws Exception {
        String casa = casaEHospede("Casa PDV")[0];
        String suco = criar("/produtos", """
                {"codigo": "SUCO", "nome": "Suco", "categoria": "CONSUMO", "unidade": "UN", "precoVenda": 8.00,
                 "controlaEstoque": false}""");
        String corpo = mockMvc.perform(post("/pdv/vendas").header("Idempotency-Key", "venda-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imovelId\": \"%s\", \"itens\": [{\"produtoId\": \"%s\", \"quantidade\": 2}]}"
                                .formatted(casa, suco)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.formaPagamento").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String venda = JsonPath.read(corpo, "$.data.id");

        String chave = "pagamento-" + UUID.randomUUID();
        enviar(post("/pdv/vendas/{id}/pagamento", venda).header("Idempotency-Key", chave),
                "{\"formaPagamento\": \"DINHEIRO\", \"valorRecebido\": 10.00}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("valorRecebido"));
        String pago = "{\"formaPagamento\": \"DINHEIRO\", \"valorRecebido\": 20.00}";
        enviar(post("/pdv/vendas/{id}/pagamento", venda).header("Idempotency-Key", chave), pago)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.data.pagamentoStatus").value("RECEBIDO"))
                .andExpect(jsonPath("$.data.formaPagamento").value("DINHEIRO"))
                .andExpect(jsonPath("$.data.valorPago").value(16.00))
                .andExpect(jsonPath("$.data.troco").value(4.00))
                .andExpect(jsonPath("$.data.pagoEm").isNotEmpty());
        enviar(post("/pdv/vendas/{id}/pagamento", venda).header("Idempotency-Key", chave), pago)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
        enviar(post("/pdv/vendas/{id}/pagamento", venda).header("Idempotency-Key", "outra-" + UUID.randomUUID()),
                "{\"formaPagamento\": \"PIX\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("O pagamento desta venda já foi registrado"));
        mockMvc.perform(get("/pdv/vendas")).andExpect(jsonPath("$.data.content[0].formaPagamento").value("DINHEIRO"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pagamento WHERE venda_id = ?::uuid AND reserva_id IS NULL",
                Integer.class, venda)).isEqualTo(1);
    }

    private int saldoEstoque(String imovel, String produto) {
        return jdbc.queryForObject("SELECT quantidade_atual FROM estoque_imovel WHERE imovel_id = ?::uuid "
                + "AND produto_id = ?::uuid", Integer.class, imovel, produto);
    }
}
