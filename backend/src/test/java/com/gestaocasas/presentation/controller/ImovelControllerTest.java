package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.usecase.imovel.*;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImovelController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=https://painel.exemplo.com")
@WithMockUser
class ImovelControllerTest {

    private static final String VALIDO = """
            {
              "nome": "Casa da Praia",
              "logradouro": "Rua das Conchas",
              "cidade": "Ubatuba",
              "uf": "SP",
              "capacidadeMaxima": 8,
              "quartos": 3,
              "banheiros": 2,
              "camas": 5,
              "valorBaseDiaria": 450.00,
              "horarioCheckin": "15:00",
              "horarioCheckout": "11:00",
              "comodidades": ["Wi-Fi"]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateImovelUseCase createImovel;
    @MockitoBean
    private ListImoveisUseCase listImoveis;
    @MockitoBean
    private GetImovelByIdUseCase getImovelById;
    @MockitoBean
    private UpdateImovelUseCase updateImovel;
    @MockitoBean
    private InativarImovelUseCase inativarImovel;

    private static ImovelResponse response(UUID id) {
        return new ImovelResponse(id, "Casa da Praia", null, "Rua das Conchas", null, null, null, "Ubatuba", "SP",
                null, TipoUh.CASA, null, 8, 6, 2, 3, 2, 5, new BigDecimal("450.00"), LocalTime.of(15, 0), LocalTime.of(11, 0),
                StatusImovel.ATIVO, Set.of("Wi-Fi"), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void criaImovelERetornaEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(createImovel.execute(any())).thenReturn(response(id));

        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.valorBaseDiaria").value(450.00))
                .andExpect(jsonPath("$.data.horarioCheckin").value("15:00:00"))
                .andExpect(jsonPath("$.data.status").value("ATIVO"));
    }

    @Test
    void rejeitaImovelInvalidoComErrosPorCampo() throws Exception {
        String invalido = """
                {
                  "nome": " ",
                  "logradouro": "Rua A",
                  "cidade": "Santos",
                  "uf": "SPX",
                  "cep": "123",
                  "capacidadeMaxima": 0,
                  "quartos": -1,
                  "banheiros": 1,
                  "camas": 1,
                  "valorBaseDiaria": 10.999,
                  "horarioCheckin": "15:00"
                }
                """;

        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("nome", "uf", "cep",
                        "capacidadeMaxima", "quartos", "valorBaseDiaria")));
        verifyNoInteractions(createImovel);
    }

    @Test
    void casaExigeEnderecoECapacidade() throws Exception {
        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Casa", "quartos": 1, "banheiros": 1, "camas": 1, "valorBaseDiaria": 0,
                         "capacidadeCriancas": -1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("logradouro", "cidade", "uf",
                        "capacidadeAdultos", "capacidadeCriancas")));
        verifyNoInteractions(createImovel);
    }

    @Test
    void imovelCompletoSemEnderecoPassaNaValidacao() throws Exception {
        when(createImovel.execute(any())).thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Imóvel completo", "tipo": "IMOVEL_COMPLETO", "capacidadeAdultos": 20,
                         "capacidadeCriancas": 10, "quartos": 12, "banheiros": 6, "camas": 20,
                         "valorBaseDiaria": 0}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void rejeitaJsonMalformado() throws Exception {
        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON).content("{ \"nome\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejeitaStatusDesconhecidoNoCorpo() throws Exception {
        mockMvc.perform(post("/imoveis").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"uf\": \"SP\"", "\"uf\": \"SP\", \"status\": \"VENDIDO\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retorna404QuandoImovelNaoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(getImovelById.execute(id)).thenThrow(new ImovelNotFoundException(id));

        mockMvc.perform(get("/imoveis/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Imóvel não encontrado: " + id));
    }

    @Test
    void retorna400ParaIdQueNaoEhUuid() throws Exception {
        mockMvc.perform(get("/imoveis/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void repassaFiltrosEPaginacaoNaListagem() throws Exception {
        when(listImoveis.execute(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/imoveis").param("status", "MANUTENCAO").param("nome", "praia")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(listImoveis).execute(eq(StatusImovel.MANUTENCAO), eq("praia"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void limitaTamanhoDaPaginaACem() throws Exception {
        when(listImoveis.execute(any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mockMvc.perform(get("/imoveis").param("size", "5000")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(listImoveis).execute(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void rejeitaFiltroDeStatusInvalido() throws Exception {
        mockMvc.perform(get("/imoveis").param("status", "QUALQUER")).andExpect(status().isBadRequest());
    }

    @Test
    void naoExisteExclusaoFisicaDeImovel() throws Exception {
        mockMvc.perform(delete("/imoveis/{id}", UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void corsAceitaApenasOrigemConfigurada() throws Exception {
        mockMvc.perform(options("/imoveis").header("Origin", "https://painel.exemplo.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://painel.exemplo.com"));

        mockMvc.perform(options("/imoveis").header("Origin", "https://outro-site.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
