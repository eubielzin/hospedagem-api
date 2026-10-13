package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.application.dto.AjusteEstoqueRequest;
import com.gestaocasas.application.dto.MovimentacaoEstoqueResponse;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.application.service.MovimentacaoIdempotente;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class RegistrarAjusteEstoqueUseCase {

    private final MovimentadorEstoque movimentador;
    private final MovimentacaoIdempotente idempotente;
    private final EstoqueMapper mapper;

    public RegistrarAjusteEstoqueUseCase(MovimentadorEstoque movimentador, MovimentacaoIdempotente idempotente,
                                         EstoqueMapper mapper) {
        this.movimentador = movimentador;
        this.idempotente = idempotente;
        this.mapper = mapper;
    }

    /** @param chaveIdempotencia opcional; com ela, repetir a requisição não lança o ajuste de novo */
    @Transactional
    public ResultadoIdempotente<MovimentacaoEstoqueResponse> execute(UUID imovelId, AjusteEstoqueRequest request,
                                                                     String chaveIdempotencia) {
        String conteudo = String.join("|", imovelId.toString(), String.valueOf(request.produtoId()),
                String.valueOf(request.tipo()), String.valueOf(request.quantidade()), String.valueOf(request.motivo()));
        return idempotente.executar(OperacaoIdempotente.AJUSTE_ESTOQUE, chaveIdempotencia, conteudo,
                () -> movimentador.movimentar(imovelId, request.produtoId(), request.tipo().movimentacao(),
                        request.quantidade(), request.motivo(), null, null))
                .map(mapper::toResponse);
    }
}
