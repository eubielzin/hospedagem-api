package com.gestaocasas.presentation.controller;

import com.gestaocasas.application.usecase.produto.*;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.infrastructure.config.CorsConfig;
import com.gestaocasas.infrastructure.config.GlobalExceptionHandler;
import com.gestaocasas.infrastructure.config.SecurityConfig;
import com.gestaocasas.infrastructure.security.JwtConfig;
import com.gestaocasas.infrastructure.security.JwtEmissorDeToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Produtos são administrativos: sem token válido nenhuma rota responde, nem as de leitura (a
 * listagem expõe preço de custo), e nenhum caso de uso é chamado. Os tokens aqui são reais, assinados
 * com o segredo dos testes, para exercitar a validação de assinatura, emissor e expiração.
 */
@WebMvcTest(ProdutoController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class ProdutoAcessoSemTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private CreateProdutoUseCase createProduto;
    @MockitoBean
    private ListProdutosUseCase listProdutos;
    @MockitoBean
    private GetProdutoByIdUseCase getProdutoById;
    @MockitoBean
    private UpdateProdutoUseCase updateProduto;
    @MockitoBean
    private InativarProdutoUseCase inativarProduto;
    @MockitoBean
    private BuscarProdutosUseCase buscarProdutos;

    private final UUID id = UUID.randomUUID();

    @Test
    void semTokenTodasAsRotasRespondem401() throws Exception {
        String corpo = "{\"codigo\":\"X\"}";

        mockMvc.perform(get("/produtos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"));
        mockMvc.perform(get("/produtos/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/produtos/{id}", id).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/produtos/{id}/inativar", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/produtos/buscar").param("termo", "coca")).andExpect(status().isUnauthorized());

        verifyNoInteractions(createProduto, listProdutos, getProdutoById, updateProduto, inativarProduto,
                buscarProdutos);
    }

    @Test
    void tokenEmitidoNoLoginDaAcesso() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Ana");
        usuario.setEmail("ana@exemplo.com");
        String token = new JwtEmissorDeToken(jwtEncoder, Duration.ofHours(1)).emitir(usuario).token();

        mockMvc.perform(get("/produtos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(getProdutoById).execute(id);
    }

    @Test
    void tokenExpiradoResponde401() throws Exception {
        Instant emitidoEm = Instant.now().minus(Duration.ofHours(3));
        String token = assinar(jwtEncoder, JwtClaimsSet.builder()
                .issuer("gestao-casas-api").subject(UUID.randomUUID().toString())
                .issuedAt(emitidoEm).expiresAt(emitidoEm.plus(Duration.ofHours(1))).build());

        recusa(token);
    }

    @Test
    void tokenDeOutroEmissorResponde401() throws Exception {
        recusa(assinar(jwtEncoder, claimsValidas().issuer("outro-sistema").build()));
    }

    @Test
    void tokenAssinadoComOutroSegredoResponde401() throws Exception {
        JwtEncoder outro = new JwtConfig("um-segredo-diferente-com-mais-de-32-caracteres").jwtEncoder();

        recusa(assinar(outro, claimsValidas().build()));
    }

    @Test
    void tokenMalformadoResponde401() throws Exception {
        recusa("isto-nao-e-um-jwt");
    }

    private void recusa(String token) throws Exception {
        mockMvc.perform(get("/produtos/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"));
        verifyNoInteractions(getProdutoById);
    }

    private static JwtClaimsSet.Builder claimsValidas() {
        Instant agora = Instant.now();
        return JwtClaimsSet.builder().issuer("gestao-casas-api").subject(UUID.randomUUID().toString())
                .issuedAt(agora).expiresAt(agora.plus(Duration.ofHours(1)));
    }

    private static String assinar(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
