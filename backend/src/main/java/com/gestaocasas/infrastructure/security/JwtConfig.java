package com.gestaocasas.infrastructure.security;

import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Tokens JWT assinados com HMAC-SHA256 pela própria API: quem emite (login) é quem valida (rotas
 * protegidas), então basta um segredo compartilhado, definido em JWT_SECRET.
 */
@Configuration
public class JwtConfig {

    static final String EMISSOR = "gestao-casas-api";
    private static final int TAMANHO_MINIMO_SEGREDO = 32;

    private final SecretKey chave;

    public JwtConfig(@Value("${app.auth.jwt.secret:}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        // Falha na subida: sem segredo forte, qualquer um poderia forjar tokens.
        if (bytes.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException("JWT_SECRET ausente ou curto demais: use ao menos "
                    + TAMANHO_MINIMO_SEGREDO + " caracteres (por exemplo, a saída de \"openssl rand -base64 48\")");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(chave).build();
    }

    /**
     * Além de assinatura, emissor e expiração, confere a cada requisição se o dono do token continua
     * ativo: inativar um usuário corta o acesso na hora, sem esperar o token expirar. Nos testes de
     * fatia web (sem JPA) o repositório não existe e essa conferência fica de fora.
     */
    @Bean
    public JwtDecoder jwtDecoder(ObjectProvider<UsuarioRepository> usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> padrao = JwtValidators.createDefaultWithIssuer(EMISSOR);
        UsuarioRepository repository = usuarios.getIfAvailable();
        decoder.setJwtValidator(repository == null ? padrao
                : new DelegatingOAuth2TokenValidator<>(padrao, new UsuarioAtivoValidator(repository)));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
