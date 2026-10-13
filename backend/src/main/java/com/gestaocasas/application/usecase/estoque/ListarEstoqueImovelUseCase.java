package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ListarEstoqueImovelUseCase {

    private final ImovelRepository imovelRepository;
    private final EstoqueImovelRepository estoqueRepository;
    private final EstoqueMapper mapper;

    public ListarEstoqueImovelUseCase(ImovelRepository imovelRepository, EstoqueImovelRepository estoqueRepository,
                                      EstoqueMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.estoqueRepository = estoqueRepository;
        this.mapper = mapper;
    }

    /** Produtos que controlam estoque, em ordem de nome; os que o imóvel nunca teve aparecem com saldo zero. */
    @Transactional(readOnly = true)
    public PageResponse<EstoqueItemResponse> execute(UUID imovelId, String nome, Boolean ativo, Pageable pageable) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        String padraoNome = nome == null || nome.isBlank() ? "%" : "%" + escapeLike(nome.trim().toLowerCase()) + "%";
        // A ordenação é fixa na consulta: a página pedida pelo cliente não carrega ordenação.
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(estoqueRepository
                .listarPorImovel(imovelId, padraoNome, ativo == null, Boolean.TRUE.equals(ativo), pagina)
                .map(item -> mapper.toResponse(imovelId, item.getProduto(), item.getEstoque())));
    }

    private static String escapeLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
