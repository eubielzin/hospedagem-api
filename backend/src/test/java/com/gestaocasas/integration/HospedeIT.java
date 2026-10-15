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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Busca, unicidade do documento e inativação de hóspedes contra o PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(UsuarioAutenticado.class)
class HospedeIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        BancoDeTeste.registrar(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static String json(String nome, String tipo, String numero) {
        return """
                {"nome":"%s","tipoDocumento":"%s","numeroDocumento":"%s","nacionalidade":"Brasileira",
                 "telefone":"(98) 90000-0000"}""".formatted(nome, tipo, numero);
    }

    private String criar(String nome, String tipo, String numero) throws Exception {
        String corpo = mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(nome, tipo, numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.data.id");
    }

    @Test
    void buscaPorNomeSemAcentoEPorParteDoDocumentoFormatado() throws Exception {
        criar("Busca João Araújo", "CPF", "529.982.247-25");
        criar("Busca Ana Beatriz", "PASSAPORTE", "fx123987");
        criar("Busca Bruno Lima", "RG", "");

        mockMvc.perform(get("/hospedes").param("busca", "joao ARAUJO"))
                .andExpect(jsonPath("$.data[*].nome", contains("Busca João Araújo")))
                .andExpect(jsonPath("$.data[0].numeroDocumento").value("52998224725"));
        mockMvc.perform(get("/hospedes").param("busca", "982.247"))
                .andExpect(jsonPath("$.data[*].nome", contains("Busca João Araújo")));
        mockMvc.perform(get("/hospedes").param("busca", "123987"))
                .andExpect(jsonPath("$.data[*].nome", contains("Busca Ana Beatriz")))
                .andExpect(jsonPath("$.data[0].numeroDocumento").value("FX123987"));
        // Curinga de LIKE é texto comum.
        mockMvc.perform(get("/hospedes").param("busca", "%"))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/hospedes").param("busca", "busca"))
                .andExpect(jsonPath("$.data[*].nome", contains("Busca Ana Beatriz", "Busca Bruno Lima", "Busca João Araújo")))
                .andExpect(jsonPath("$.data[1].numeroDocumento").value(""));
    }

    @Test
    void mesmoDocumentoComOutraFormatacaoResponde409MesmoInativo() throws Exception {
        String id = criar("Carla Souza", "CPF", "11144477735");
        mockMvc.perform(patch("/hospedes/{id}/inativar", id)).andExpect(status().isOk());

        mockMvc.perform(post("/hospedes").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Outra Pessoa", "CPF", "111.444.777-35")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um hóspede com este documento (Carla Souza)."));

        // Mesmo número em outro tipo de documento é outro documento.
        criar("Outra Pessoa", "RG", "11144477735");
        // Vários hóspedes sem documento convivem.
        criar("Sem Documento 1", "CPF", "");
        criar("Sem Documento 2", "CPF", "");
    }

    @Test
    void edicaoMantemOProprioDocumentoERecusaODeOutro() throws Exception {
        String id = criar("Diego Melo", "RG", "MG-12.345.678");
        criar("Elisa Melo", "RG", "SP-99");

        mockMvc.perform(put("/hospedes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Diego Melo Filho", "RG", "mg12345678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nome").value("Diego Melo Filho"));
        mockMvc.perform(put("/hospedes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("Diego Melo Filho", "RG", "sp 99")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT numero_documento FROM hospede WHERE id = ?", String.class,
                UUID.fromString(id))).isEqualTo("MG12345678");
    }

    @Test
    void inativadoSaiDaListaPadraoEVoltaAoReativar() throws Exception {
        String id = criar("Fabio Inativo", "CPF", "");

        mockMvc.perform(patch("/hospedes/{id}/inativar", id))
                .andExpect(jsonPath("$.data.ativo").value(false));
        mockMvc.perform(get("/hospedes"))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(id))));
        mockMvc.perform(get("/hospedes").param("incluirInativos", "true"))
                .andExpect(jsonPath("$.data[*].id", hasItem(id)));

        mockMvc.perform(patch("/hospedes/{id}/reativar", id))
                .andExpect(jsonPath("$.data.ativo").value(true));
        mockMvc.perform(get("/hospedes/{id}/historico", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/hospedes/{id}/historico", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
