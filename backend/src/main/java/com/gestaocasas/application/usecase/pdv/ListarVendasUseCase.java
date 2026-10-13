package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.dto.VendaResumoResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ListarVendasUseCase {

    private static final Sort MAIS_RECENTES_PRIMEIRO = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));

    private final VendaRepository repository;
    private final VendaMapper mapper;

    public ListarVendasUseCase(VendaRepository repository, VendaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<VendaResumoResponse> execute(UUID imovelId, StatusVenda status, Pageable pageable) {
        List<Specification<Venda>> filtros = new ArrayList<>();
        if (imovelId != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("imovel").get("id"), imovelId));
        }
        if (status != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MAIS_RECENTES_PRIMEIRO);
        return PageResponse.from(repository.findAll(Specification.allOf(filtros), pagina).map(mapper::toResumo));
    }
}
