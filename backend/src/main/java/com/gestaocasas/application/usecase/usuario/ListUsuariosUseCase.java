package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListUsuariosUseCase {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;

    public ListUsuariosUseCase(UsuarioRepository repository, UsuarioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> execute() {
        return repository.findAll(Sort.by("nome")).stream().map(mapper::toResponse).toList();
    }
}
