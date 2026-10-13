package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.MovimentacaoEstoqueRepository;
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
public class ListarMovimentacoesEstoqueUseCase {

    private static final Sort MAIS_RECENTES_PRIMEIRO = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));

    private final ImovelRepository imovelRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final EstoqueMapper mapper;

    public ListarMovimentacoesEstoqueUseCase(ImovelRepository imovelRepository,
                                             MovimentacaoEstoqueRepository movimentacaoRepository,
                                             EstoqueMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovimentacaoEstoqueResponse> execute(UUID imovelId, UUID produtoId, TipoMovimentacao tipo,
                                                             Pageable pageable) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        List<Specification<MovimentacaoEstoque>> filtros = new ArrayList<>();
        filtros.add((root, query, cb) -> cb.equal(root.get("imovel").get("id"), imovelId));
        if (produtoId != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("produto").get("id"), produtoId));
        }
        if (tipo != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("tipo"), tipo));
        }
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), MAIS_RECENTES_PRIMEIRO);
        return PageResponse.from(movimentacaoRepository.findAll(Specification.allOf(filtros), pagina)
                .map(mapper::toResponse));
    }
}
