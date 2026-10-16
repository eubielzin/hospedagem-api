package com.gestaocasas.application.usecase.categoria;

import com.gestaocasas.application.dto.CategoriaUhResponse;
import com.gestaocasas.application.mapper.CategoriaUhMapper;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListCategoriasUseCase {

    private final CategoriaUhRepository repository;
    private final CategoriaUhMapper mapper;

    public ListCategoriasUseCase(CategoriaUhRepository repository, CategoriaUhMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CategoriaUhResponse> execute() {
        return repository.findAllByOrderByNivelAscNomeAsc().stream().map(mapper::toResponse).toList();
    }
}
