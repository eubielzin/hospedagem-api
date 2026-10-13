package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class ListProdutosUseCase {

    private final ProdutoRepository repository;
    private final ProdutoMapper mapper;

    public ListProdutosUseCase(ProdutoRepository repository, ProdutoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProdutoResponse> execute(String nome, CategoriaProduto categoria, Boolean ativo,
                                                 Pageable pageable) {
        List<Specification<Produto>> filtros = new ArrayList<>();
        if (nome != null && !nome.isBlank()) {
            String padrao = "%" + escapeLike(nome.trim().toLowerCase()) + "%";
            filtros.add((root, query, cb) -> cb.like(cb.lower(root.get("nome")), padrao, '\\'));
        }
        if (categoria != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("categoria"), categoria));
        }
        if (ativo != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("ativo"), ativo));
        }
        return PageResponse.from(repository.findAll(Specification.allOf(filtros), pageable).map(mapper::toResponse));
    }

    private static String escapeLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
