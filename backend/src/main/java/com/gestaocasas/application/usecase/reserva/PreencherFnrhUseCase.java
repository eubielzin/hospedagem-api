package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.FnrhRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Fnrh;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** RF-23: FNRH da reserva, exigida no check-in (RN-04). Preencher de novo substitui a anterior. */
@Component
public class PreencherFnrhUseCase {

    private final ReservaRepository repository;
    private final AcoesReserva acoes;
    private final ReservaMapper mapper;

    public PreencherFnrhUseCase(ReservaRepository repository, AcoesReserva acoes, ReservaMapper mapper) {
        this.repository = repository;
        this.acoes = acoes;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse execute(UUID id, FnrhRequest request) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, "preencher a FNRH", StatusReserva.RESERVADO, StatusReserva.HOSPEDADO);
        Fnrh fnrh = new Fnrh();
        fnrh.setCidadeProcedencia(request.cidadeProcedencia().trim());
        fnrh.setCidadeDestino(request.cidadeDestino().trim());
        fnrh.setMotivoViagem(request.motivoViagem());
        fnrh.setMeioTransporte(request.meioTransporte());
        fnrh.setEmitidaEm(acoes.hoje());
        reserva.setFnrh(fnrh);
        acoes.registrar(reserva, "FNRH preenchida");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }
}
