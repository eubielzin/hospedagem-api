package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.EntradaEstoqueRequest;
import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class RegistrarEntradaEstoqueUseCase {

    private final MovimentadorEstoque movimentador;
    private final EstoqueMapper mapper;

    public RegistrarEntradaEstoqueUseCase(MovimentadorEstoque movimentador, EstoqueMapper mapper) {
        this.movimentador = movimentador;
        this.mapper = mapper;
    }

    @Transactional
    public MovimentacaoEstoqueResponse execute(UUID imovelId, EntradaEstoqueRequest request) {
        return mapper.toResponse(movimentador.movimentar(imovelId, request.produtoId(), TipoMovimentacao.ENTRADA,
                request.quantidade(), request.motivo(), null, null));
    }
}
