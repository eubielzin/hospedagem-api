package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.application.validation.ProdutoUnicidadeValidator;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateProdutoUseCase {

    private final ProdutoRepository repository;
    private final ProdutoMapper mapper;
    private final ProdutoUnicidadeValidator unicidade;

    public UpdateProdutoUseCase(ProdutoRepository repository, ProdutoMapper mapper,
                                ProdutoUnicidadeValidator unicidade) {
        this.repository = repository;
        this.mapper = mapper;
        this.unicidade = unicidade;
    }

    @Transactional
    public ProdutoResponse execute(UUID id, ProdutoRequest request) {
        Produto produto = repository.findById(id).orElseThrow(() -> new ProdutoNotFoundException(id));
        // Antes de alterar a entidade: a consulta de unicidade dispararia o flush das mudanças.
        unicidade.validar(request, id);
        mapper.updateEntity(request, produto);
        if (request.ativo() != null) {
            produto.setAtivo(request.ativo());
        }
        return mapper.toResponse(repository.saveAndFlush(produto));
    }
}
