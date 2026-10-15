package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.usecase.reserva.*;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.ConflitoException;
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

import java.time.LocalDate;
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

@WebMvcTest(ReservaController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class ReservaControllerTest {

    private static final String VALIDA = """
            {"uhId": "%s", "titularId": "%s", "canalId": "%s", "checkin": "2027-02-27", "checkout": "2027-03-06",
             "adultos": 2, "criancas": 0, "valorTotal": 3360.00}"""
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListReservasUseCase listReservas;
    @MockitoBean
    private GetReservaByIdUseCase getReservaById;
    @MockitoBean
    private CreateReservaUseCase createReserva;
    @MockitoBean
    private UpdateReservaUseCase updateReserva;
    @MockitoBean
    private AlterarStatusReservaUseCase alterarStatus;

    @Test
    void repassaOsFiltrosDaTela() throws Exception {
        UUID canal = UUID.randomUUID();

        mockMvc.perform(get("/reservas").param("busca", "CM-1546").param("status", "RESERVADO")
                        .param("canalId", canal.toString()).param("inicio", "2027-01-01").param("fim", "2027-01-31"))
                .andExpect(status().isOk());

        verify(listReservas).execute("CM-1546", StatusReserva.RESERVADO, canal, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31));
    }

    @Test
    void statusOuDataInvalidosRespondem400() throws Exception {
        mockMvc.perform(get("/reservas").param("status", "QUALQUER")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/reservas").param("inicio", "27/02/2027")).andExpect(status().isBadRequest());
        verifyNoInteractions(listReservas);
    }

    @Test
    void criaReserva() throws Exception {
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isCreated());
        verify(createReserva).execute(any());
    }

    @Test
    void validaCampos() throws Exception {
        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adultos\": 0, \"criancas\": -1, \"valorTotal\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("uhId", "titularId", "canalId",
                        "checkin", "checkout", "adultos", "criancas", "valorTotal")));
        verifyNoInteractions(createReserva);
    }

    @Test
    void conflitoResponde409() throws Exception {
        when(createReserva.execute(any())).thenThrow(new ConflitoException("A UH 'Casa' já está reservada"));

        mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A UH 'Casa' já está reservada"));
    }

    @Test
    void acoesDeStatus() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/reservas/{id}/checkin", id)).andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/checkout", id)).andExpect(status().isOk());
        mockMvc.perform(post("/reservas/{id}/cancelar", id)).andExpect(status().isOk());
        mockMvc.perform(put("/reservas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALIDA))
                .andExpect(status().isOk());

        verify(alterarStatus).checkin(id);
        verify(alterarStatus).checkout(id);
        verify(alterarStatus).cancelar(eq(id), eq(null));
        verify(updateReserva).execute(eq(id), any());
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/reservas").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/canais").with(anonymous())).andExpect(status().isUnauthorized());
        verifyNoInteractions(listReservas);
    }
}
