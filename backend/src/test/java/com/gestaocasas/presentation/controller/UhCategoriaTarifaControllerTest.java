package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.application.usecase.categoria.ListCategoriasUseCase;
import com.gestaocasas.application.usecase.categoria.SalvarCategoriaUseCase;
import com.gestaocasas.application.usecase.tarifa.ListTarifasUseCase;
import com.gestaocasas.application.usecase.tarifa.SalvarTarifaUseCase;
import com.gestaocasas.application.usecase.uh.AlterarGovernancaUseCase;
import com.gestaocasas.application.usecase.uh.ListUhsUseCase;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
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

@WebMvcTest({UhController.class, CategoriaUhController.class, TarifaController.class})
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
@WithMockUser
class UhCategoriaTarifaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListUhsUseCase listUhs;
    @MockitoBean
    private AlterarGovernancaUseCase alterarGovernanca;
    @MockitoBean
    private ListCategoriasUseCase listCategorias;
    @MockitoBean
    private SalvarCategoriaUseCase salvarCategoria;
    @MockitoBean
    private ListTarifasUseCase listTarifas;
    @MockitoBean
    private SalvarTarifaUseCase salvarTarifa;

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/uhs").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/categorias").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tarifas").with(anonymous())).andExpect(status().isUnauthorized());
        verifyNoInteractions(listUhs, listCategorias, listTarifas);
    }

    @Test
    void governancaRepassaOStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(alterarGovernanca.execute(eq(id), any())).thenReturn(new UhResponse(id, "Casa Flor", TipoUh.CASA, null,
                3, 1, StatusGovernanca.SUJA, true));

        mockMvc.perform(patch("/uhs/{id}/governanca", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SUJA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusGovernanca").value("SUJA"));
        verify(alterarGovernanca).execute(id, StatusGovernanca.SUJA);
    }

    @Test
    void governancaRecusaStatusCalculadoOuAusente() throws Exception {
        for (String corpo : new String[]{"{\"status\": \"BLOQUEADA\"}", "{\"status\": \"OCUPADA\"}", "{}"}) {
            mockMvc.perform(patch("/uhs/{id}/governanca", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("status"));
        }
        verifyNoInteractions(alterarGovernanca);
    }

    @Test
    void regraDeNegocioSaiComOCodigo() throws Exception {
        when(alterarGovernanca.execute(any(), any()))
                .thenThrow(new RegraDeNegocioException("RN-07", "Casa Flor está ocupada: o status muda no check-out."));

        mockMvc.perform(patch("/uhs/{id}/governanca", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"LIVRE\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("RN-07"))
                .andExpect(jsonPath("$.message").value("Casa Flor está ocupada: o status muda no check-out."));
    }

    @Test
    void tarifaInvalidaResponde400PorCampo() throws Exception {
        mockMvc.perform(post("/tarifas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "", "inicio": "2027-01-10", "fim": "2027-01-09", "valorDiaria": 10.001,
                         "estadiaMinima": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("categoriaId", "nome", "fim",
                        "valorDiaria", "fechadoChegada", "fechadoSaida", "vendaFechada")));
        verifyNoInteractions(salvarTarifa);
    }
}
