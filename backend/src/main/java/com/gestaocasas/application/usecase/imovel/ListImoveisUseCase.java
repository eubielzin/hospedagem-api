package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelResumoResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class ListImoveisUseCase {

    private final ImovelRepository repository;
    private final ImovelMapper mapper;

    public ListImoveisUseCase(ImovelRepository repository, ImovelMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ImovelResumoResponse> execute(StatusImovel status, String nome, Pageable pageable) {
        List<Specification<Imovel>> filtros = new ArrayList<>();
        if (status != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (nome != null && !nome.isBlank()) {
            String padrao = "%" + escapeLike(nome.trim().toLowerCase()) + "%";
            filtros.add((root, query, cb) -> cb.like(cb.lower(root.get("nome")), padrao, '\\'));
        }
        return PageResponse.from(repository.findAll(Specification.allOf(filtros), pageable).map(mapper::toResumo));
    }

    private static String escapeLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
