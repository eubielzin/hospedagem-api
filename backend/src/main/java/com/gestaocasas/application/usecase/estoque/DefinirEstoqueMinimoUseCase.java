package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.EstoqueMinimoRequest;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DefinirEstoqueMinimoUseCase {

    private final ImovelRepository imovelRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueImovelRepository estoqueRepository;
    private final EstoqueMapper mapper;

    public DefinirEstoqueMinimoUseCase(ImovelRepository imovelRepository, ProdutoRepository produtoRepository,
                                       EstoqueImovelRepository estoqueRepository, EstoqueMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueRepository = estoqueRepository;
        this.mapper = mapper;
    }

    /** Altera somente o mínimo deste imóvel. A quantidade só muda por movimentações de estoque. */
    @Transactional
    public EstoqueItemResponse execute(UUID imovelId, UUID produtoId, EstoqueMinimoRequest request) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ProdutoNotFoundException(produtoId));
        if (!produto.isControlaEstoque()) {
            throw new RegraDeNegocioException("O produto '" + produto.getNome() + "' não controla estoque");
        }

        estoqueRepository.criarSeAusente(imovelId, produtoId);
        EstoqueImovel estoque = estoqueRepository.findForUpdate(imovelId, produtoId)
                .orElseThrow(() -> new IllegalStateException("Saldo não encontrado após a criação"));
        estoque.setEstoqueMinimo(request.estoqueMinimo());
        return mapper.toResponse(imovelId, produto, estoqueRepository.saveAndFlush(estoque));
    }
}
