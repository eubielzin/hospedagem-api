package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Só reservas ainda não iniciadas (RESERVADO) são editadas por inteiro, inclusive trocando de UH; o valor é recotado. */
@Component
public class UpdateReservaUseCase {

    private final ReservaRepository repository;
    private final PreenchedorReserva preenchedor;
    private final AcoesReserva acoes;
    private final ReservaMapper mapper;

    public UpdateReservaUseCase(ReservaRepository repository, PreenchedorReserva preenchedor, AcoesReserva acoes,
                                ReservaMapper mapper) {
        this.repository = repository;
        this.preenchedor = preenchedor;
        this.acoes = acoes;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse execute(UUID id, ReservaRequest request) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, "editar", StatusReserva.RESERVADO);
        String antes = RemanejarReservaUseCase.resumo(reserva);
        preenchedor.preencher(request, reserva);
        String depois = RemanejarReservaUseCase.resumo(reserva);
        acoes.registrar(reserva, antes.equals(depois) ? "Reserva editada"
                : "Reserva editada de (" + antes + ") para (" + depois + ")");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }
}
