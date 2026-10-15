package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.usecase.hospede.*;
import com.gestaocasas.domain.enums.TipoDocumento;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HospedeController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class HospedeControllerTest {

    private static final String VALIDO = """
            {
              "nome": "Maria da Silva",
              "tipoDocumento": "CPF",
              "numeroDocumento": "529.982.247-25",
              "nacionalidade": "Brasileira",
              "telefone": "(98) 90000-0000"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListHospedesUseCase listHospedes;
    @MockitoBean
    private GetHospedeByIdUseCase getHospedeById;
    @MockitoBean
    private CreateHospedeUseCase createHospede;
    @MockitoBean
    private UpdateHospedeUseCase updateHospede;
    @MockitoBean
    private InativarHospedeUseCase inativarHospede;
    @MockitoBean
    private ReativarHospedeUseCase reativarHospede;
    @MockitoBean
    private HistoricoHospedeUseCase historicoHospede;

    private final UUID id = UUID.randomUUID();

    private HospedeResponse response(boolean ativo) {
        return new HospedeResponse(id, "Maria da Silva", TipoDocumento.CPF, "52998224725", "Brasileira",
                "(98) 90000-0000", ativo, Instant.now(), Instant.now());
    }

    @Test
    void listaComBuscaEInativos() throws Exception {
        when(listHospedes.execute("maria", true)).thenReturn(List.of(response(true)));

        mockMvc.perform(get("/hospedes").param("busca", "maria").param("incluirInativos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.data[0].tipoDocumento").value("CPF"))
                .andExpect(jsonPath("$.data[0].nacionalidade").value("Brasileira"))
                .andExpect(jsonPath("$.data[0].telefone").value("(98) 90000-0000"))
                .andExpect(jsonPath("$.data[0].email").doesNotExist());
    }

    @Test
    void listaSemParametrosNaoIncluiInativos() throws Exception {
        mockMvc.perform(get("/hospedes")).andExpect(status().isOk());

        verify(listHospedes).execute(null, false);
    }

    @Test
    void cadastra() throws Exception {
        when(createHospede.execute(any())).thenReturn(response(true));

        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.ativo").value(true));
    }

    @Test
    void documentoOpcionalNoCadastro() throws Exception {
        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"tipoDocumento\":\"CPF\",\"numeroDocumento\":\"\","
                                + "\"nacionalidade\":\"Brasileira\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cpfInvalidoApareceNoCampoDoNumero() throws Exception {
        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("529.982.247-25", "111.222.333-44")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("numeroDocumento"))
                .andExpect(jsonPath("$.errors[0].message").value("CPF inválido"));

        verifyNoInteractions(createHospede);
    }

    @Test
    void numeroQueNaoECpfNaoPassaPelaValidacaoDeCpf() throws Exception {
        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"CPF\"", "\"RG\"").replace("529.982.247-25", "12.345.678-9")))
                .andExpect(status().isCreated());
    }

    @Test
    void validaCamposObrigatoriosEFormato() throws Exception {
        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"nacionalidade\":\" \",\"telefone\":\"%s\"}".formatted("9".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("nome", "tipoDocumento", "nacionalidade", "telefone")));

        verifyNoInteractions(createHospede);
    }

    @Test
    void tipoDeDocumentoDesconhecidoResponde400() throws Exception {
        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDO.replace("\"CPF\"", "\"CNH\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentoDuplicadoResponde409() throws Exception {
        when(createHospede.execute(any()))
                .thenThrow(new ConflitoException("Já existe um hóspede com este documento (João)."));

        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um hóspede com este documento (João)."));
    }

    @Test
    void atualizaInativaEReativa() throws Exception {
        when(updateHospede.execute(eq(id), any())).thenReturn(response(true));
        when(inativarHospede.execute(id)).thenReturn(response(false));
        when(reativarHospede.execute(id)).thenReturn(response(true));

        mockMvc.perform(put("/hospedes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/hospedes/{id}/inativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(false));
        mockMvc.perform(patch("/hospedes/{id}/reativar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(true));
    }

    @Test
    void consultaEHistorico() throws Exception {
        when(getHospedeById.execute(id)).thenReturn(response(true));
        when(historicoHospede.execute(id)).thenReturn(List.of());

        mockMvc.perform(get("/hospedes/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/hospedes/{id}/historico", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void hospedeInexistenteResponde404() throws Exception {
        when(getHospedeById.execute(id)).thenThrow(new HospedeNotFoundException(id));

        mockMvc.perform(get("/hospedes/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/hospedes").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/hospedes").with(anonymous()).contentType(MediaType.APPLICATION_JSON).content(VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(listHospedes, createHospede);
    }
}
