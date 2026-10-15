package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.RedefinirSenhaRequest;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Define a senha de outro usuário, por exemplo quando ele a esqueceu. Não pede a senha atual. */
@Component
public class RedefinirSenhaUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public RedefinirSenhaUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(UUID id, RedefinirSenhaRequest request) {
        Usuario usuario = repository.findById(id).orElseThrow(() -> new UsuarioNotFoundException(id));
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
    }
}
