package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.dto.TransferenciaEstoqueRequest;
import com.gestaocasas.application.dto.TransferenciaEstoqueResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class TransferirEstoqueUseCase {

    private final ImovelRepository imovelRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentadorEstoque movimentador;
    private final EstoqueMapper mapper;

    public TransferirEstoqueUseCase(ImovelRepository imovelRepository, ProdutoRepository produtoRepository,
                                    MovimentadorEstoque movimentador, EstoqueMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.produtoRepository = produtoRepository;
        this.movimentador = movimentador;
        this.mapper = mapper;
    }

    /** Saída da origem e entrada no destino na mesma transação: se uma falhar, nenhuma acontece. */
    @Transactional
    public TransferenciaEstoqueResponse execute(TransferenciaEstoqueRequest request) {
        UUID origem = request.imovelOrigemId();
        UUID destino = request.imovelDestinoId();
        UUID produtoId = request.produtoId();
        if (origem.equals(destino)) {
            throw new RegraDeNegocioException("Origem e destino da transferência devem ser imóveis diferentes");
        }
        for (UUID imovelId : new UUID[]{origem, destino}) {
            if (!imovelRepository.existsById(imovelId)) {
                throw new ImovelNotFoundException(imovelId);
            }
        }
        if (!produtoRepository.existsById(produtoId)) {
            throw new ProdutoNotFoundException(produtoId);
        }

        // Ordem fixa de bloqueio: transferências simultâneas A→B e B→A não travam uma à outra.
        boolean origemPrimeiro = origem.compareTo(destino) < 0;
        movimentador.travar(origemPrimeiro ? origem : destino, produtoId);
        movimentador.travar(origemPrimeiro ? destino : origem, produtoId);

        UUID transferenciaId = UUID.randomUUID();
        MovimentacaoEstoqueResponse saida = mapper.toResponse(movimentador.movimentar(origem, produtoId,
                TipoMovimentacao.TRANSFERENCIA_SAIDA, request.quantidade(), request.motivo(),
                TipoReferenciaMovimentacao.TRANSFERENCIA, transferenciaId));
        MovimentacaoEstoqueResponse entrada = mapper.toResponse(movimentador.movimentar(destino, produtoId,
                TipoMovimentacao.TRANSFERENCIA_ENTRADA, request.quantidade(), request.motivo(),
                TipoReferenciaMovimentacao.TRANSFERENCIA, transferenciaId));
        return new TransferenciaEstoqueResponse(transferenciaId, saida, entrada);
    }
}
