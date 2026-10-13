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
public class ListarAlertasEstoqueUseCase {

    private final ImovelRepository imovelRepository;
    private final EstoqueImovelRepository estoqueRepository;
    private final EstoqueMapper mapper;

    public ListarAlertasEstoqueUseCase(ImovelRepository imovelRepository, EstoqueImovelRepository estoqueRepository,
                                       EstoqueMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.estoqueRepository = estoqueRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<EstoqueItemResponse> execute(UUID imovelId, Pageable pageable) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(estoqueRepository.listarAlertas(imovelId, pagina)
                .map(estoque -> mapper.toResponse(imovelId, estoque.getProduto(), estoque)));
    }
}
