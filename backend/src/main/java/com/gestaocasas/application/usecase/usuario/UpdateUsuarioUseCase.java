package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.dto.UsuarioUpdateRequest;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Component
public class UpdateUsuarioUseCase {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;

    public UpdateUsuarioUseCase(UsuarioRepository repository, UsuarioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public UsuarioResponse execute(UUID id, UsuarioUpdateRequest request) {
        Usuario usuario = repository.findById(id).orElseThrow(() -> new UsuarioNotFoundException(id));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflitoException("Já existe um usuário com o e-mail " + email);
        }
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        return mapper.toResponse(repository.saveAndFlush(usuario));
    }
}
