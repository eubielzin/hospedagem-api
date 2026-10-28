package com.gestaocasas.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reserva completa contra o PostgreSQL real: cotação pelas tarifas, RN-02 com o imóvel completo, bloqueios,
 * FNRH, remanejamento, no-show e histórico.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class ReservaCompletaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    private String categoria;
    private String hospede;
    private String direto;

    private String criar(String url, String json) throws Exception {
        String corpo = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao,
                                 String json) throws Exception {
        return mockMvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @BeforeEach
    void cadastros() throws Exception {
        if (categoria != null) {
            return;
        }
        categoria = criar("/categorias", "{\"nome\": \"Categoria %s\", \"nivel\": 1}".formatted(UUID.randomUUID()));
        criar("/tarifas", tarifa("Tarifa base", HOJE.minusDays(10), HOJE.plusDays(400), "400.00", 1));
        hospede = criar("/hospedes", """
                {"nome": "Helena Duarte %s", "tipoDocumento": "PASSAPORTE", "numeroDocumento": "X%s",
                 "nacionalidade": "Portuguesa", "dataNascimento": "1990-04-02", "email": "helena@exemplo.pt",
                 "endereco": "Lisboa/Portugal", "observacoesAlimentares": "Sem glúten"}"""
                .formatted(UUID.randomUUID(), Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000)));
        String canais = mockMvc.perform(get("/canais")).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(canais, "$.data[?(@.nome == 'Direto (recepção)')].id");
        direto = ids.getFirst();
    }

    private String tarifa(String nome, LocalDate inicio, LocalDate fim, String valor, int estadiaMinima) {
        return """
                {"categoriaId": "%s", "nome": "%s", "inicio": "%s", "fim": "%s", "valorDiaria": %s,
                 "estadiaMinima": %d, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoria, nome, inicio, fim, valor, estadiaMinima);
    }

    private String uh(String nome, String tipo) throws Exception {
        return criar("/imoveis", """
                {"nome": "%s", "tipo": "%s", "categoriaId": "%s", "logradouro": "Rua das Dunas", "cidade": "Barreirinhas",
                 "uf": "MA", "capacidadeAdultos": 4, "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1,
                 "camas": 3, "valorBaseDiaria": 0}""".formatted(nome, tipo, categoria));
    }

    private String reserva(String uh, LocalDate checkin, LocalDate checkout) {
        return """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s",
                 "adultos": 2, "criancas": 1, "regimePensao": "CAFE_DA_MANHA", "codigoExterno": " "}"""
                .formatted(uh, hospede, direto, checkin, checkout);
    }

    private void fnrh(String reserva) throws Exception {
        enviar(put("/reservas/{id}/fnrh", reserva), """
                {"cidadeProcedencia": "Lisboa/Portugal", "cidadeDestino": "Barreirinhas/MA",
                 "motivoViagem": "LAZER", "meioTransporte": "AVIAO"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fnrh.motivoViagem").value("LAZER"))
                .andExpect(jsonPath("$.data.fnrh.emitidaEm").value(HOJE.toString()));
    }

    @Test
    void hospedeGuardaOsCamposNovos() throws Exception {
        mockMvc.perform(get("/hospedes/{id}", hospede))
                .andExpect(jsonPath("$.data.dataNascimento").value("1990-04-02"))
                .andExpect(jsonPath("$.data.email").value("helena@exemplo.pt"))
                .andExpect(jsonPath("$.data.endereco").value("Lisboa/Portugal"))
                .andExpect(jsonPath("$.data.observacoesAlimentares").value("Sem glúten"));
        enviar(post("/hospedes"), """
                {"nome": "Inválido", "tipoDocumento": "RG", "nacionalidade": "Brasileira", "email": "nao-e-email",
                 "dataNascimento": "%s"}""".formatted(HOJE.plusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("email",
                        "dataNascimento")));
    }

    @Test
    void cotacaoPelasTarifasEValorDaReserva() throws Exception {
        String casa = uh("Casa Cotação", "CASA");
        LocalDate entrada = HOJE.plusDays(60);
        criar("/tarifas", tarifa("Feriado", entrada.plusDays(1), entrada.plusDays(1), "550.00", 1));

        mockMvc.perform(get("/reservas/cotacao").param("uhId", casa).param("checkin", entrada.toString())
                        .param("checkout", entrada.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noites").value(3))
                .andExpect(jsonPath("$.data.valorTotal").value(1350.00))
                .andExpect(jsonPath("$.data.diarias[*].tarifa", contains("Tarifa base", "Feriado", "Tarifa base")));
        mockMvc.perform(get("/reservas/cotacao").param("uhId", casa).param("checkin", HOJE.plusDays(500).toString())
                        .param("checkout", HOJE.plusDays(501).toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RF-31"));
        mockMvc.perform(get("/reservas/cotacao").param("uhId", casa).param("checkin", entrada.toString())
                        .param("checkout", entrada.toString()))
                .andExpect(status().isBadRequest());

        // O valor da reserva é o da cotação, e a edição recota.
        String id = criar("/reservas", reserva(casa, entrada, entrada.plusDays(3)));
        mockMvc.perform(get("/reservas/{id}", id))
                .andExpect(jsonPath("$.data.valorTotal").value(1350.00))
                .andExpect(jsonPath("$.data.regimePensao").value("CAFE_DA_MANHA"))
                .andExpect(jsonPath("$.data.codigoExterno").isEmpty())
                .andExpect(jsonPath("$.data.historico[0].descricao").value("Reserva criada (Direto (recepção)) por "
                        + "usuário não identificado"));
        enviar(put("/reservas/{id}", id), reserva(casa, entrada.plusDays(2), entrada.plusDays(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valorTotal").value(800.00))
                .andExpect(jsonPath("$.data.historico[1].descricao", startsWith("Reserva editada de (Casa Cotação, ")));
        // A listagem não traz o histórico.
        mockMvc.perform(get("/reservas").param("busca", "Casa Cotação"))
                .andExpect(jsonPath("$.data[0].historico").isEmpty());
    }

    @Test
    void imovelCompletoConflitaComAsCasasEBloqueios() throws Exception {
        String alegria = uh("Casa Alegria RN02", "CASA");
        String flor = uh("Casa Flor RN02", "CASA");
        String completo = criar("/imoveis", """
                {"nome": "Imóvel completo RN02", "tipo": "IMOVEL_COMPLETO", "categoriaId": "%s", "capacidadeAdultos": 20,
                 "capacidadeCriancas": 10, "quartos": 12, "banheiros": 6, "camas": 20, "valorBaseDiaria": 0}"""
                .formatted(categoria));
        LocalDate entrada = HOJE.plusDays(120);

        criar("/reservas", reserva(alegria, entrada, entrada.plusDays(3)));
        // Casa reservada: o completo não entra (RN-02); outra casa entra.
        enviar(post("/reservas"), reserva(completo, entrada.plusDays(2), entrada.plusDays(4)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-02"))
                .andExpect(jsonPath("$.message", startsWith("Casa Alegria RN02 já tem a reserva CM-")));
        criar("/reservas", reserva(flor, entrada, entrada.plusDays(3)));
        // E no outro sentido: completo reservado, nenhuma casa entra.
        criar("/reservas", reserva(completo, entrada.plusDays(10), entrada.plusDays(12)));
        enviar(post("/reservas"), reserva(flor, entrada.plusDays(11), entrada.plusDays(13)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-02"));

        // Bloqueio: fim inclusive; conflita com reservas e com outros bloqueios.
        enviar(post("/bloqueios"), """
                {"uhId": "%s", "inicio": "%s", "fim": "%s", "motivo": "Pintura"}"""
                .formatted(alegria, entrada.plusDays(2), entrada.plusDays(5)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-01"));
        String pintura = criar("/bloqueios", """
                {"uhId": "%s", "inicio": "%s", "fim": "%s", "motivo": " Pintura "}"""
                .formatted(alegria, entrada.plusDays(3), entrada.plusDays(5)));
        enviar(post("/reservas"), reserva(alegria, entrada.plusDays(5), entrada.plusDays(6)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-01"))
                .andExpect(jsonPath("$.message").value("Casa Alegria RN02 está bloqueada de "
                        + br(entrada.plusDays(3)) + " a " + br(entrada.plusDays(5)) + " (Pintura)."));
        criar("/reservas", reserva(alegria, entrada.plusDays(6), entrada.plusDays(7)));
        enviar(post("/bloqueios"), """
                {"uhId": "%s", "inicio": "%s", "fim": "%s", "motivo": "Uso próprio"}"""
                .formatted(completo, entrada.plusDays(4), entrada.plusDays(4)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-02"));
        enviar(post("/bloqueios"), """
                {"uhId": "%s", "inicio": "%s", "fim": "%s", "motivo": " "}"""
                .formatted(alegria, entrada, entrada.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("fim", "motivo")));

        mockMvc.perform(get("/bloqueios").param("inicio", entrada.toString()).param("fim", entrada.plusDays(3).toString()))
                .andExpect(jsonPath("$.data[*].id", contains(pintura)))
                .andExpect(jsonPath("$.data[0].motivo").value("Pintura"))
                .andExpect(jsonPath("$.data[0].uhId").value(alegria))
                .andExpect(jsonPath("$.data[0].ativo").value(true));
        mockMvc.perform(patch("/bloqueios/{id}/encerrar", pintura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));
        mockMvc.perform(get("/bloqueios")).andExpect(jsonPath("$.data[*].id", org.hamcrest.Matchers.not(hasItem(pintura))));
        criar("/reservas", reserva(alegria, entrada.plusDays(4), entrada.plusDays(5)));
        mockMvc.perform(patch("/bloqueios/{id}/encerrar", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void bloqueioVigenteApareceNaGovernanca() throws Exception {
        String casa = uh("Casa Bloqueada Hoje", "CASA");
        String bloqueio = criar("/bloqueios", """
                {"uhId": "%s", "inicio": "%s", "fim": "%s", "motivo": "Dedetização"}"""
                .formatted(casa, HOJE, HOJE));
        mockMvc.perform(get("/uhs"))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].statusGovernanca".formatted(casa), contains("BLOQUEADA")));
        mockMvc.perform(patch("/bloqueios/{id}/encerrar", bloqueio)).andExpect(status().isOk());
        mockMvc.perform(get("/uhs"))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].statusGovernanca".formatted(casa), contains("LIVRE")));
    }

    @Test
    void remanejamentoComOHospedeNaCasa() throws Exception {
        String sol = uh("Casa Sol", "CASA");
        String lua = uh("Casa Lua", "CASA");
        String id = criar("/reservas", reserva(sol, HOJE, HOJE.plusDays(3)));

        // Reservado: muda tudo, e o valor é recotado.
        enviar(put("/reservas/{id}/remanejamento", id), """
                {"uhId": "%s", "checkin": "%s", "checkout": "%s"}""".formatted(sol, HOJE, HOJE.plusDays(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valorTotal").value(800.00));
        fnrh(id);
        mockMvc.perform(post("/reservas/{id}/checkin", id)).andExpect(status().isOk());

        // Hospedado: a entrada não muda (RN-03).
        enviar(put("/reservas/{id}/remanejamento", id), """
                {"uhId": "%s", "checkin": "%s", "checkout": "%s"}""".formatted(lua, HOJE.minusDays(1), HOJE.plusDays(2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-03"));
        // Troca de casa com o hóspede dentro: a antiga fica suja e a nova, ocupada.
        enviar(put("/reservas/{id}/remanejamento", id), """
                {"uhId": "%s", "checkin": "%s", "checkout": "%s"}""".formatted(lua, HOJE, HOJE.plusDays(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uhNome").value("Casa Lua"))
                .andExpect(jsonPath("$.data.valorTotal").value(1600.00))
                .andExpect(jsonPath("$.data.historico[*].descricao", hasItem(startsWith("Remanejada de (Casa Sol, "))));
        mockMvc.perform(get("/uhs"))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].statusGovernanca".formatted(sol), contains("SUJA")))
                .andExpect(jsonPath("$.data[?(@.id == '%s')].statusGovernanca".formatted(lua), contains("OCUPADA")));
        // UH inexistente.
        enviar(put("/reservas/{id}/remanejamento", id), """
                {"uhId": "%s", "checkin": "%s", "checkout": "%s"}""".formatted(UUID.randomUUID(), HOJE, HOJE.plusDays(4)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/reservas/{id}", id))
                .andExpect(jsonPath("$.data.historico[*].descricao", hasItem("FNRH preenchida")))
                .andExpect(jsonPath("$.data.historico[*].descricao", hasItem("Check-in realizado")));
    }

    @Test
    void noShowECancelamentoComMultaLiberamAUh() throws Exception {
        String casa = uh("Casa Encerramentos", "CASA");
        LocalDate entrada = HOJE.plusDays(200);
        String naoVeio = criar("/reservas", reserva(casa, entrada, entrada.plusDays(2)));

        enviar(post("/reservas/{id}/no-show", naoVeio), "{\"motivo\": \"\"}")
                .andExpect(status().isBadRequest());
        enviar(post("/reservas/{id}/no-show", naoVeio), """
                {"motivo": "Não compareceu", "multa": 400.00, "valorRetido": 400.00, "valorDevolver": 0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.data.encerramento.data").value(HOJE.toString()))
                .andExpect(jsonPath("$.data.encerramento.valorRetido").value(400.00))
                .andExpect(jsonPath("$.data.motivoCancelamento").isEmpty());

        String desistiu = criar("/reservas", reserva(casa, entrada, entrada.plusDays(2)));
        enviar(post("/reservas/{id}/cancelamento", desistiu), """
                {"motivo": "Mudou de planos", "multa": 200.00, "valorRetido": 200.00, "valorDevolver": 600.00}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"))
                .andExpect(jsonPath("$.data.encerramento.valorDevolver").value(600.00))
                .andExpect(jsonPath("$.data.motivoCancelamento").value("Mudou de planos"));
        enviar(post("/reservas/{id}/no-show", desistiu), "{\"motivo\": \"Outro\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-03"))
                .andExpect(jsonPath("$.message").value("Não é possível registrar o no-show com a reserva em \"Cancelada\"."));
        criar("/reservas", reserva(casa, entrada, entrada.plusDays(2)));
    }

    private static String br(LocalDate data) {
        return data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
