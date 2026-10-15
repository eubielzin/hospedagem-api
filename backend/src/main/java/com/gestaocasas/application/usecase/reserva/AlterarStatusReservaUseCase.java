package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.CancelamentoReservaRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Ciclo de vida: RESERVADO → HOSPEDADO (check-in) → CHECKOUT_REALIZADO (check-out), ou
 * RESERVADO → CANCELADA. Cancelada libera a UH para outras reservas no mesmo período.
 */
@Component
public class AlterarStatusReservaUseCase {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaRepository repository;
    private final ReservaMapper mapper;
    private final Clock clock;

    public AlterarStatusReservaUseCase(ReservaRepository repository, ReservaMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** RN-04: o documento do titular é exigido no check-in, não no cadastro. */
    @Transactional
    public ReservaResponse checkin(UUID id) {
        Reserva reserva = buscar(id);
        exigirStatus(reserva, StatusReserva.RESERVADO, "fazer check-in");
        if (LocalDate.now(clock).isBefore(reserva.getCheckin())) {
            throw new RegraDeNegocioException("O check-in só pode ser feito a partir de "
                    + reserva.getCheckin().format(DATA));
        }
        if (reserva.getTitular().getNumeroDocumento().isEmpty()) {
            throw new RegraDeNegocioException("Informe o documento do hóspede '" + reserva.getTitular().getNome()
                    + "' antes do check-in");
        }
        reserva.setStatus(StatusReserva.HOSPEDADO);
        reserva.setCheckinRealizadoEm(agora());
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    @Transactional
    public ReservaResponse checkout(UUID id) {
        Reserva reserva = buscar(id);
        exigirStatus(reserva, StatusReserva.HOSPEDADO, "fazer check-out");
        reserva.setStatus(StatusReserva.CHECKOUT_REALIZADO);
        reserva.setCheckoutRealizadoEm(agora());
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    @Transactional
    public ReservaResponse cancelar(UUID id, CancelamentoReservaRequest request) {
        Reserva reserva = buscar(id);
        exigirStatus(reserva, StatusReserva.RESERVADO, "cancelar");
        reserva.setStatus(StatusReserva.CANCELADA);
        reserva.setCanceladaEm(agora());
        String motivo = request == null || request.motivo() == null || request.motivo().isBlank()
                ? null : request.motivo().trim();
        reserva.setMotivoCancelamento(motivo);
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    private Reserva buscar(UUID id) {
        return repository.findDetalhadaById(id).orElseThrow(() -> new ReservaNotFoundException(id));
    }

    private static void exigirStatus(Reserva reserva, StatusReserva esperado, String acao) {
        if (reserva.getStatus() != esperado) {
            throw new RegraDeNegocioException("Não é possível " + acao + " de uma reserva com status "
                    + reserva.getStatus());
        }
    }

    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
