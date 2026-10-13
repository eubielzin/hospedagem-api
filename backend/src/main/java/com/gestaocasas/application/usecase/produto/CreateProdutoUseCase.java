package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.application.validation.ProdutoUnicidadeValidator;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateProdutoUseCase {

    private final ProdutoRepository repository;
    private final ProdutoMapper mapper;
    private final ProdutoUnicidadeValidator unicidade;

    public CreateProdutoUseCase(ProdutoRepository repository, ProdutoMapper mapper,
                                ProdutoUnicidadeValidator unicidade) {
        this.repository = repository;
        this.mapper = mapper;
        this.unicidade = unicidade;
    }

    @Transactional
    public ProdutoResponse execute(ProdutoRequest request) {
        unicidade.validar(request, null);
        Produto produto = new Produto();
        mapper.updateEntity(request, produto);
        produto.setAtivo(request.ativo() == null || request.ativo());
        return mapper.toResponse(repository.saveAndFlush(produto));
    }
}
