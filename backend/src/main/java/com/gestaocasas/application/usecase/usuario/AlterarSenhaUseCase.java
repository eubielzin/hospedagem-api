package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.AlterarSenhaRequest;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AlterarSenhaUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AlterarSenhaUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(UUID usuarioId, AlterarSenhaRequest request) {
        Usuario usuario = repository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNotFoundException(usuarioId));
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta");
        }
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
    }
}
