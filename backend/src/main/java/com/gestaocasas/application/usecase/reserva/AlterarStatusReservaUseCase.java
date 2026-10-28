package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.application.service.ContaReserva;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Check-in (RF-24) e check-out (RF-25). Cancelamento e no-show ficam em EncerrarReservaUseCase. */
@Component
public class AlterarStatusReservaUseCase {

    private final ReservaRepository repository;
    private final GovernancaUh governanca;
    private final ContaReserva conta;
    private final AcoesReserva acoes;
    private final ReservaMapper mapper;

    public AlterarStatusReservaUseCase(ReservaRepository repository, GovernancaUh governanca, ContaReserva conta,
                                       AcoesReserva acoes, ReservaMapper mapper) {
        this.repository = repository;
        this.governanca = governanca;
        this.conta = conta;
        this.acoes = acoes;
        this.mapper = mapper;
    }

    /** RN-04: documento do titular e FNRH. RF-35: a UH precisa estar liberada pela governança. */
    @Transactional
    public ReservaResponse checkin(UUID id) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, "fazer o check-in", StatusReserva.RESERVADO);
        if (acoes.hoje().isBefore(reserva.getCheckin())) {
            throw new RegraDeNegocioException("RN-03", "O check-in só pode ser feito a partir de "
                    + Formato.data(reserva.getCheckin()) + ".");
        }
        if (reserva.getTitular().getNumeroDocumento().isBlank()) {
            throw new RegraDeNegocioException("RN-04", "Informe o documento do hóspede titular antes do check-in.");
        }
        if (reserva.getFnrh() == null) {
            throw new RegraDeNegocioException("RN-04", "Preencha a FNRH antes do check-in.");
        }
        governanca.exigirLiberada(reserva.getImovel());
        reserva.setStatus(StatusReserva.HOSPEDADO);
        reserva.setCheckinRealizadoEm(acoes.agora());
        acoes.registrar(reserva, "Check-in realizado");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    /**
     * RN-05: com saldo em aberto, só com a pendência autorizada (RN-14: pelo gerente, quando houver perfis).
     * RN-07: a UH fica suja e só volta a livre pela governança.
     */
    @Transactional
    public ReservaResponse checkout(UUID id, boolean autorizarPendencia) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, "fazer o check-out", StatusReserva.HOSPEDADO);
        BigDecimal saldo = conta.saldo(reserva);
        boolean comSaldo = saldo.signum() > 0;
        if (comSaldo && !autorizarPendencia) {
            throw new RegraDeNegocioException("RN-05", "A conta tem saldo de " + Formato.dinheiro(saldo)
                    + ". Quite ou registre a pendência autorizada pelo gerente.");
        }
        reserva.setStatus(StatusReserva.CHECKOUT_REALIZADO);
        reserva.setCheckoutRealizadoEm(acoes.agora());
        reserva.setPendenciaAutorizada(comSaldo);
        governanca.marcarSuja(reserva.getImovel());
        acoes.registrar(reserva, comSaldo ? "Check-out com pendência autorizada de " + Formato.dinheiro(saldo)
                + " por " + acoes.quem() : "Check-out realizado");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }
}
