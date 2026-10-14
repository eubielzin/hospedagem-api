package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetUsuarioAtualUseCase {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;

    public GetUsuarioAtualUseCase(UsuarioRepository repository, UsuarioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse execute(UUID usuarioId) {
        return repository.findById(usuarioId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new UsuarioNotFoundException(usuarioId));
    }
}
