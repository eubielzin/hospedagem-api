package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioRequest;
import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class CreateUsuarioUseCase {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper mapper;

    public CreateUsuarioUseCase(UsuarioRepository repository, PasswordEncoder passwordEncoder,
                                UsuarioMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Transactional
    public UsuarioResponse execute(UsuarioRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe um usuário com o e-mail " + email);
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setAtivo(true);
        return mapper.toResponse(repository.saveAndFlush(usuario));
    }
}
