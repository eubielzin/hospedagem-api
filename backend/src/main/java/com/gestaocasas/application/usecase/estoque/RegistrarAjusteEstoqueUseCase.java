package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.AjusteEstoqueRequest;
import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.application.service.MovimentadorEstoque;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class RegistrarAjusteEstoqueUseCase {

    private final MovimentadorEstoque movimentador;
    private final EstoqueMapper mapper;

    public RegistrarAjusteEstoqueUseCase(MovimentadorEstoque movimentador, EstoqueMapper mapper) {
        this.movimentador = movimentador;
        this.mapper = mapper;
    }

    @Transactional
    public MovimentacaoEstoqueResponse execute(UUID imovelId, AjusteEstoqueRequest request) {
        return mapper.toResponse(movimentador.movimentar(imovelId, request.produtoId(),
                request.tipo().movimentacao(), request.quantidade(), request.motivo(), null, null));
    }
}
