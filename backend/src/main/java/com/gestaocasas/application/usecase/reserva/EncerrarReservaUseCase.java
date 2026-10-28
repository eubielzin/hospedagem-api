package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.EncerramentoRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.domain.entity.Encerramento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF-07 e RN-03: só reservas ainda não iniciadas são canceladas ou marcadas como no-show; as duas liberam a UH.
 * RN-08: o valor da reserva deixa de ser receita; o valor retido, não.
 */
@Component
public class EncerrarReservaUseCase {

    private final ReservaRepository repository;
    private final AcoesReserva acoes;
    private final ReservaMapper mapper;

    public EncerrarReservaUseCase(ReservaRepository repository, AcoesReserva acoes, ReservaMapper mapper) {
        this.repository = repository;
        this.acoes = acoes;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse cancelar(UUID id, EncerramentoRequest request) {
        return encerrar(id, request, StatusReserva.CANCELADA, "cancelar");
    }

    @Transactional
    public ReservaResponse noShow(UUID id, EncerramentoRequest request) {
        return encerrar(id, request, StatusReserva.NO_SHOW, "registrar o no-show");
    }

    private ReservaResponse encerrar(UUID id, EncerramentoRequest request, StatusReserva status, String acao) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, acao, StatusReserva.RESERVADO);
        Encerramento encerramento = new Encerramento();
        encerramento.setData(acoes.hoje());
        encerramento.setMotivo(request.motivo() == null || request.motivo().isBlank() ? null : request.motivo().trim());
        encerramento.setMulta(valor(request.multa()));
        encerramento.setValorRetido(valor(request.valorRetido()));
        encerramento.setValorDevolver(valor(request.valorDevolver()));
        reserva.setStatus(status);
        reserva.setEncerramento(encerramento);
        reserva.setEncerradaEm(acoes.agora());
        acoes.registrar(reserva, status.rotulo()
                + (encerramento.getMotivo() == null ? "" : ": " + encerramento.getMotivo())
                + " (multa " + Formato.dinheiro(encerramento.getMulta())
                + ", retido " + Formato.dinheiro(encerramento.getValorRetido())
                + ", a devolver " + Formato.dinheiro(encerramento.getValorDevolver()) + ")");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    private static BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO.setScale(2) : valor.setScale(2);
    }
}
