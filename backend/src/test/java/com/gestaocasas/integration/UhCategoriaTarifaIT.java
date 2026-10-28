package com.gestaocasas.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UHs, categorias, tarifas e governança contra o PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class UhCategoriaTarifaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    private String criar(String url, String json) throws Exception {
        String corpo = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    private String categoria(String nome, int nivel) throws Exception {
        return criar("/categorias", """
                {"nome": "%s", "nivel": %d, "descricao": "  "}""".formatted(nome, nivel));
    }

    private String casa(String nome, String categoriaId) throws Exception {
        return criar("/imoveis", """
                {"nome": "%s", "tipo": "CASA", "categoriaId": "%s", "logradouro": "Rua das Dunas", "numero": "7",
                 "cidade": "Barreirinhas", "uf": "MA", "cep": "65590-000", "capacidadeAdultos": 4,
                 "capacidadeCriancas": 2, "quartos": 2, "banheiros": 1, "camas": 3, "valorBaseDiaria": 0}"""
                .formatted(nome, categoriaId));
    }

    private static String tarifa(String categoriaId, String nome, LocalDate inicio, LocalDate fim, String valor) {
        return """
                {"categoriaId": "%s", "nome": "%s", "inicio": "%s", "fim": "%s", "valorDiaria": %s,
                 "estadiaMinima": 1, "fechadoChegada": false, "fechadoSaida": false, "vendaFechada": false}"""
                .formatted(categoriaId, nome, inicio, fim, valor);
    }

    @Test
    void categoriasCadastroEdicaoEConflitoDeNome() throws Exception {
        String id = categoria("Suíte Família", 2);
        categoria("Suíte Tripla", 1);

        mockMvc.perform(get("/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.nome =~ /Suíte.*/)].nome", contains("Suíte Tripla", "Suíte Família")))
                .andExpect(jsonPath("$.data[?(@.nome == 'Suíte Família')].descricao", contains((Object) null)))
                .andExpect(jsonPath("$.data[?(@.nome == 'Suíte Família')].ativa", contains(true)));

        mockMvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"suíte família\", \"nivel\": 3}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/categorias/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Suíte Tripla\", \"nivel\": 3}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/categorias/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Suíte Família Comfort\", \"nivel\": 3, \"descricao\": \"2 quartos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Suíte Família Comfort"))
                .andExpect(jsonPath("$.data.nivel").value(3))
                .andExpect(jsonPath("$.data.descricao").value("2 quartos"));
        mockMvc.perform(put("/categorias/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Outra\", \"nivel\": 1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \" \", \"nivel\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("nome", "nivel")));
    }

    /** As outras classes de teste também cadastram tarifas: filtra as da categoria. */
    private static String daCategoria(String categoriaId, String campo) {
        return "$.data[?(@.categoriaId == '%s')].%s".formatted(categoriaId, campo);
    }

    @Test
    void tarifasOrdenadasPorInicioEValidadas() throws Exception {
        String categoria = categoria("Categoria Tarifas", 1);
        String alta = criar("/tarifas", tarifa(categoria, "Alta temporada", HOJE.plusDays(25), HOJE.plusDays(45),
                "620.00"));
        criar("/tarifas", tarifa(categoria, "Tarifa base", HOJE.minusDays(30), HOJE.plusDays(300), "480.00"));

        mockMvc.perform(get("/tarifas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(daCategoria(categoria, "nome"), contains("Tarifa base", "Alta temporada")))
                .andExpect(jsonPath(daCategoria(categoria, "valorDiaria"), contains(480.00, 620.00)))
                .andExpect(jsonPath(daCategoria(categoria, "fim"),
                        contains(HOJE.plusDays(300).toString(), HOJE.plusDays(45).toString())));

        mockMvc.perform(put("/tarifas/{id}", alta).contentType(MediaType.APPLICATION_JSON)
                        .content(tarifa(categoria, "Alta temporada", HOJE.plusDays(25), HOJE.plusDays(25), "650.00")
                                .replace("\"vendaFechada\": false", "\"vendaFechada\": true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valorDiaria").value(650.00))
                .andExpect(jsonPath("$.data.vendaFechada").value(true));

        // fim antes do início, diária zero e estadia mínima zero: 400 em cada campo.
        mockMvc.perform(post("/tarifas").contentType(MediaType.APPLICATION_JSON)
                        .content(tarifa(categoria, "Inválida", HOJE, HOJE.minusDays(1), "0")
                                .replace("\"estadiaMinima\": 1", "\"estadiaMinima\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("fim", "valorDiaria", "estadiaMinima")));
        mockMvc.perform(post("/tarifas").contentType(MediaType.APPLICATION_JSON)
                        .content(tarifa(UUID.randomUUID().toString(), "Sem categoria", HOJE, HOJE, "100")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/tarifas/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(tarifa(categoria, "Nenhuma", HOJE, HOJE, "100")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uhsGovernancaOcupadaCalculadaEImovelCompleto() throws Exception {
        String categoria = categoria("Categoria Governança", 1);
        String flor = casa("Casa Flor", categoria);
        String alegria = casa("Casa Alegria", categoria);

        // O imóvel completo sem endereço recebe o da primeira casa e sai depois das casas na lista.
        String completo = criar("/imoveis", """
                {"nome": "Imóvel completo", "tipo": "IMOVEL_COMPLETO", "categoriaId": "%s",
                 "capacidadeAdultos": 20, "capacidadeCriancas": 10, "quartos": 12, "banheiros": 6, "camas": 20,
                 "valorBaseDiaria": 0}""".formatted(categoria));
        mockMvc.perform(get("/imoveis/{id}", completo))
                .andExpect(jsonPath("$.data.logradouro").value("Rua das Dunas"))
                .andExpect(jsonPath("$.data.cidade").value("Barreirinhas"))
                .andExpect(jsonPath("$.data.capacidadeMaxima").value(30))
                // Sem horário próprio: o padrão da pousada.
                .andExpect(jsonPath("$.data.horarioCheckin").value("14:00:00"))
                .andExpect(jsonPath("$.data.horarioCheckout").value("12:00:00"));
        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Outro completo", "tipo": "IMOVEL_COMPLETO", "capacidadeAdultos": 2,
                         "quartos": 1, "banheiros": 1, "camas": 1, "valorBaseDiaria": 0}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um imóvel completo: 'Imóvel completo'"));

        mockMvc.perform(get("/uhs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].nome", contains("Casa Alegria", "Casa Flor", "Imóvel completo")))
                .andExpect(jsonPath("$.data[0].tipo").value("CASA"))
                .andExpect(jsonPath("$.data[0].categoriaId").value(categoria))
                .andExpect(jsonPath("$.data[0].capacidadeAdultos").value(4))
                .andExpect(jsonPath("$.data[0].capacidadeCriancas").value(2))
                .andExpect(jsonPath("$.data[0].statusGovernanca").value("LIVRE"))
                .andExpect(jsonPath("$.data[0].ativa").value(true));

        mockMvc.perform(patch("/uhs/{id}/governanca", flor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUJA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusGovernanca").value("SUJA"));
        mockMvc.perform(patch("/uhs/{id}/governanca", flor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"OCUPADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        mockMvc.perform(patch("/uhs/{id}/governanca", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"LIVRE\"}"))
                .andExpect(status().isNotFound());

        // Hóspede no imóvel completo: todas as casas saem OCUPADA e não mudam à mão (RN-07).
        String hospede = criar("/hospedes", """
                {"nome": "Hóspede Governança", "tipoDocumento": "CPF", "numeroDocumento": "52998224725",
                 "nacionalidade": "Brasileira"}""");
        String corpoCanais = mockMvc.perform(get("/canais")).andReturn().getResponse().getContentAsString();
        List<String> direto = JsonPath.read(corpoCanais, "$.data[?(@.nome == 'Direto (recepção)')].id");
        criar("/tarifas", tarifa(categoria, "Tarifa base", HOJE.minusDays(5), HOJE.plusDays(30), "2600.00"));
        String reserva = criar("/reservas", """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s",
                 "adultos": 10, "criancas": 2}"""
                .formatted(completo, hospede, direto.getFirst(), HOJE, HOJE.plusDays(2)));
        mockMvc.perform(put("/reservas/{id}/fnrh", reserva).contentType(MediaType.APPLICATION_JSON).content("""
                        {"cidadeProcedencia": "Recife/PE", "cidadeDestino": "Barreirinhas/MA",
                         "motivoViagem": "LAZER", "meioTransporte": "AVIAO"}"""))
                .andExpect(status().isOk());
        // RF-35: a Casa Flor está suja; o imóvel completo inclui ela.
        mockMvc.perform(post("/reservas/{id}/checkin", reserva))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RF-35"))
                .andExpect(jsonPath("$.message").value("Casa Flor ainda não foi liberada pela governança."));
        mockMvc.perform(patch("/uhs/{id}/governanca", flor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"LIVRE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/checkin", reserva)).andExpect(status().isOk());

        mockMvc.perform(get("/uhs"))
                .andExpect(jsonPath("$.data[*].statusGovernanca", contains("OCUPADA", "OCUPADA", "OCUPADA")));
        mockMvc.perform(patch("/uhs/{id}/governanca", alegria).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MANUTENCAO\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-07"))
                .andExpect(jsonPath("$.message").value("Casa Alegria está ocupada: o status muda no check-out."));

        // RN-07: no check-out, o imóvel completo e todas as casas ficam sujos.
        mockMvc.perform(post("/reservas/{id}/checkout", reserva)).andExpect(status().isOk());
        mockMvc.perform(get("/uhs"))
                .andExpect(jsonPath("$.data[*].statusGovernanca", contains("SUJA", "SUJA", "SUJA")));
    }
}
