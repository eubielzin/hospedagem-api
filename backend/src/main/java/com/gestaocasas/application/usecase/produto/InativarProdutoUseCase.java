package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class InativarProdutoUseCase {

    private final ProdutoRepository repository;
    private final ProdutoMapper mapper;

    public InativarProdutoUseCase(ProdutoRepository repository, ProdutoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ProdutoResponse execute(UUID id) {
        Produto produto = repository.findById(id).orElseThrow(() -> new ProdutoNotFoundException(id));
        produto.setAtivo(false);
        return mapper.toResponse(repository.saveAndFlush(produto));
    }
}
