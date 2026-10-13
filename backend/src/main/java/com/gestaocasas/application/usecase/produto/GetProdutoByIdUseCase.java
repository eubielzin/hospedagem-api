package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetProdutoByIdUseCase {

    private final ProdutoRepository repository;
    private final ProdutoMapper mapper;

    public GetProdutoByIdUseCase(ProdutoRepository repository, ProdutoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProdutoResponse execute(UUID id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> new ProdutoNotFoundException(id));
    }
}
