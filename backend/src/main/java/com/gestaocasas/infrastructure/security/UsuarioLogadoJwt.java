package com.gestaocasas.infrastructure.security;

import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** O usuário é o sujeito do token JWT (o id da tabela usuario). */
@Component
public class UsuarioLogadoJwt implements UsuarioLogado {

    private final UsuarioRepository repository;

    public UsuarioLogadoJwt(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Usuario> atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return repository.findById(UUID.fromString(jwt.getSubject()));
    }
}
