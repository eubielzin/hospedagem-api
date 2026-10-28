package com.gestaocasas.application.usecase.conta;

import com.gestaocasas.application.dto.ContaResponse;
import com.gestaocasas.application.service.ContaReserva;
import com.gestaocasas.application.usecase.reserva.AcoesReserva;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetContaUseCase {

    private final AcoesReserva acoes;
    private final ContaReserva conta;

    public GetContaUseCase(AcoesReserva acoes, ContaReserva conta) {
        this.acoes = acoes;
        this.conta = conta;
    }

    @Transactional(readOnly = true)
    public ContaResponse execute(UUID reservaId) {
        return conta.calcular(acoes.buscar(reservaId));
    }
}
