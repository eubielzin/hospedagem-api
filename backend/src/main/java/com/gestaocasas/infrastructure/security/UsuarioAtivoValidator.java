package com.gestaocasas.infrastructure.security;

import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Recusa o token de usuário inativado ou removido depois da emissão. */
class UsuarioAtivoValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error USUARIO_INATIVO =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Usuário inativo ou inexistente", null);

    private final UsuarioRepository repository;

    UsuarioAtivoValidator(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(USUARIO_INATIVO);
        }
        return repository.existsByIdAndAtivoTrue(id)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(USUARIO_INATIVO);
    }
}
