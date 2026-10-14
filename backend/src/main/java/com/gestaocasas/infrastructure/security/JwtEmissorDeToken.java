package com.gestaocasas.infrastructure.security;

import com.gestaocasas.application.service.EmissorDeToken;
import com.gestaocasas.domain.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JwtEmissorDeToken implements EmissorDeToken {

    private final JwtEncoder encoder;
    private final Duration validade;

    public JwtEmissorDeToken(JwtEncoder encoder, @Value("${app.auth.jwt.validade:8h}") Duration validade) {
        this.encoder = encoder;
        this.validade = validade;
    }

    @Override
    public TokenEmitido emitir(Usuario usuario) {
        // O JWT guarda segundos: truncar mantém expiraEm igual ao "exp" do token.
        Instant agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = agora.plus(validade);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.EMISSOR)
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim("nome", usuario.getNome())
                .claim("email", usuario.getEmail())
                .build();
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new TokenEmitido(token, expiraEm);
    }
}
