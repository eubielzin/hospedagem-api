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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reservas contra o PostgreSQL real: conflito de período, filtros, ciclo de vida e concorrência. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class ReservaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    private static final LocalDate HOJE = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

    @Autowired
    private MockMvc mockMvc;

    private String criar(String url, String json) throws Exception {
        String corpo = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    private String imovel(String nome) throws Exception {
        return criar("/imoveis", """
                {"nome": "%s", "logradouro": "Rua A", "cidade": "Ubatuba", "uf": "SP", "capacidadeMaxima": 4,
                 "quartos": 2, "banheiros": 1, "camas": 2, "valorBaseDiaria": 300.00,
                 "horarioCheckin": "15:00", "horarioCheckout": "11:00"}""".formatted(nome));
    }

    private String hospede(String nome, String cpf) throws Exception {
        return criar("/hospedes", """
                {"nome": "%s", "tipoDocumento": "CPF", "numeroDocumento": "%s", "nacionalidade": "Brasileira"}"""
                .formatted(nome, cpf));
    }

    private String canal(String nome) throws Exception {
        String corpo = mockMvc.perform(get("/canais")).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(corpo, "$.data[?(@.nome == '%s')].id".formatted(nome));
        return ids.getFirst();
    }

    private static String reserva(String uh, String titular, String canal, LocalDate checkin, LocalDate checkout,
                                  int adultos) {
        return """
                {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "%s", "checkout": "%s",
                 "adultos": %d, "criancas": 0, "valorTotal": 960.00, "observacoes": " "}"""
                .formatted(uh, titular, canal, checkin, checkout, adultos);
    }

    @Test
    void canaisIniciaisVemDaMigracao() throws Exception {
        mockMvc.perform(get("/canais"))
                .andExpect(jsonPath("$.data[*].nome",
                        hasItem("Direto (recepção)")))
                .andExpect(jsonPath("$.data[*].nome",
                        org.hamcrest.Matchers.hasItems("Airbnb", "Booking.com", "Expedia")));
    }

    @Test
    void criaComCodigoENomesEImpedeConflitoDePeriodo() throws Exception {
        String casa = imovel("Casa Sabedoria");
        String cecilia = hospede("Cecília Tavares", "52998224725");
        String booking = canal("Booking.com");
        LocalDate entrada = HOJE.plusDays(30);

        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, cecilia, booking, entrada, entrada.plusDays(3), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.codigo", matchesPattern("CM-\\d{4,}")))
                .andExpect(jsonPath("$.data.uhNome").value("Casa Sabedoria"))
                .andExpect(jsonPath("$.data.titularNome").value("Cecília Tavares"))
                .andExpect(jsonPath("$.data.canalNome").value("Booking.com"))
                .andExpect(jsonPath("$.data.status").value("RESERVADO"))
                .andExpect(jsonPath("$.data.valorTotal").value(960.00))
                .andExpect(jsonPath("$.data.observacoes").isEmpty());

        // Cruza o período: recusada.
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, cecilia, booking, entrada.plusDays(2), entrada.plusDays(5), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", matchesPattern("A UH 'Casa Sabedoria' já está reservada de .* \\(CM-\\d+\\)")));
        // Começa no dia da saída da outra: permitida.
        criar("/reservas", reserva(casa, cecilia, booking, entrada.plusDays(3), entrada.plusDays(5), 2));
    }

    @Test
    void canceladaLiberaAUhECapacidadeEStatusDoImovelSaoRespeitados() throws Exception {
        String casa = imovel("Casa Amizade");
        String hospede = hospede("Bianca Pinheiro", "11144477735");
        String direto = canal("Direto (recepção)");
        LocalDate entrada = HOJE.plusDays(40);

        String id = criar("/reservas", reserva(casa, hospede, direto, entrada, entrada.plusDays(2), 2));
        mockMvc.perform(post("/reservas/{id}/cancelar", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Desistência\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELADA"))
                .andExpect(jsonPath("$.data.motivoCancelamento").value("Desistência"));
        criar("/reservas", reserva(casa, hospede, direto, entrada, entrada.plusDays(2), 2));

        mockMvc.perform(post("/reservas/{id}/cancelar", id)).andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, hospede, direto, entrada.plusDays(10), entrada.plusDays(12), 5)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RN-13"))
                .andExpect(jsonPath("$.message").value("A UH 'Casa Amizade' comporta 4 adultos e 0 crianças."));
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, hospede, direto, entrada, entrada, 2)))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(patch("/imoveis/{id}/inativar", casa)).andExpect(status().isOk());
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, hospede, direto, entrada.plusDays(20), entrada.plusDays(21), 1)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void filtrosDaTela() throws Exception {
        String flor = imovel("Filtro Casa Flor");
        String alegria = imovel("Filtro Casa Alegria");
        String otavio = hospede("Filtro Otávio Nascimento", "39053344705");
        String debora = hospede("Filtro Débora Almeida", "71428793860");
        String airbnb = canal("Airbnb");
        String expedia = canal("Expedia");
        LocalDate base = HOJE.plusDays(100);

        criar("/reservas", reserva(flor, otavio, airbnb, base, base.plusDays(3), 2));
        String deboraId = criar("/reservas", reserva(alegria, debora, expedia, base.plusDays(10), base.plusDays(12), 2));
        String codigo = JsonPath.read(mockMvc.perform(get("/reservas/{id}", deboraId))
                .andReturn().getResponse().getContentAsString(), "$.data.codigo");

        mockMvc.perform(get("/reservas").param("busca", "otavio"))
                .andExpect(jsonPath("$.data[*].titularNome", contains("Filtro Otávio Nascimento")));
        mockMvc.perform(get("/reservas").param("busca", codigo.substring(3)))
                .andExpect(jsonPath("$.data[*].codigo", contains(codigo)));
        mockMvc.perform(get("/reservas").param("busca", "casa ALEGRIA"))
                .andExpect(jsonPath("$.data[*].uhNome", contains("Filtro Casa Alegria")));
        mockMvc.perform(get("/reservas").param("busca", "filtro").param("canalId", airbnb))
                .andExpect(jsonPath("$.data[*].canalNome", contains("Airbnb")));
        // Ordem: da entrada mais recente para a mais antiga.
        mockMvc.perform(get("/reservas").param("busca", "filtro"))
                .andExpect(jsonPath("$.data[*].titularNome",
                        contains("Filtro Débora Almeida", "Filtro Otávio Nascimento")));
        // Período: só a que cruza o intervalo.
        mockMvc.perform(get("/reservas").param("busca", "filtro")
                        .param("inicio", base.plusDays(11).toString()).param("fim", base.plusDays(20).toString()))
                .andExpect(jsonPath("$.data[*].titularNome", contains("Filtro Débora Almeida")));
        mockMvc.perform(post("/reservas/{id}/cancelar", deboraId)).andExpect(status().isOk());
        mockMvc.perform(get("/reservas").param("busca", "filtro").param("status", "CANCELADA"))
                .andExpect(jsonPath("$.data[*].titularNome", contains("Filtro Débora Almeida")));

        mockMvc.perform(get("/hospedes/{id}/historico", debora))
                .andExpect(jsonPath("$.data[*].codigo", contains(codigo)))
                .andExpect(jsonPath("$.data[0].status").value("CANCELADA"));
    }

    @Test
    void cicloCheckinCheckoutEEdicao() throws Exception {
        String casa = imovel("Casa Essência");
        String comDocumento = hospede("Yasmin Moraes", "86288366757");
        String semDocumento = hospede("Sem Documento", "");
        String direto = canal("Direto (recepção)");

        String futura = criar("/reservas", reserva(casa, comDocumento, direto, HOJE.plusDays(5), HOJE.plusDays(7), 2));
        mockMvc.perform(post("/reservas/{id}/checkin", futura))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message", matchesPattern("O check-in só pode ser feito a partir de .*")));

        String hoje = criar("/reservas", reserva(casa, semDocumento, direto, HOJE, HOJE.plusDays(2), 2));
        mockMvc.perform(post("/reservas/{id}/checkin", hoje))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Informe o documento do hóspede 'Sem Documento' antes do check-in"));

        // Edição troca o titular; a própria reserva não conta como conflito.
        mockMvc.perform(put("/reservas/{id}", hoje).contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, comDocumento, direto, HOJE, HOJE.plusDays(2), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adultos").value(3));

        mockMvc.perform(post("/reservas/{id}/checkin", hoje))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HOSPEDADO"))
                .andExpect(jsonPath("$.data.checkinRealizadoEm").isNotEmpty());
        mockMvc.perform(put("/reservas/{id}", hoje).contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(casa, comDocumento, direto, HOJE, HOJE.plusDays(3), 3)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/reservas/{id}/cancelar", hoje)).andExpect(status().isUnprocessableContent());

        mockMvc.perform(post("/reservas/{id}/checkout", hoje))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKOUT_REALIZADO"));
        mockMvc.perform(post("/reservas/{id}/checkout", hoje)).andExpect(status().isUnprocessableContent());

        mockMvc.perform(get("/reservas/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(reserva(UUID.randomUUID().toString(), comDocumento, direto, HOJE, HOJE.plusDays(1), 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reservasSimultaneasParaOMesmoPeriodoCriamSoUma() throws Exception {
        String casa = imovel("Casa Concorrida");
        String hospede = hospede("Hóspede Concorrente", "15350946056");
        String direto = canal("Direto (recepção)");
        LocalDate entrada = HOJE.plusDays(200);
        String corpo = reserva(casa, hospede, direto, entrada, entrada.plusDays(2), 2);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Integer>> chamadas = IntStream.range(0, 8)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                                    .content(corpo)).andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor))
                    .toList();
            List<Integer> status = chamadas.stream().map(CompletableFuture::join).toList();
            assertThat(status).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
        } finally {
            executor.shutdown();
        }
        mockMvc.perform(get("/reservas").param("busca", "Casa Concorrida"))
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
