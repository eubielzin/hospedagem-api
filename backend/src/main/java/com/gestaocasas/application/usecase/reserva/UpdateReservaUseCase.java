package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Só reservas ainda não iniciadas (RESERVADO) podem ser editadas, inclusive trocando de UH. */
@Component
public class UpdateReservaUseCase {

    private final ReservaRepository repository;
    private final PreenchedorReserva preenchedor;
    private final ReservaMapper mapper;

    public UpdateReservaUseCase(ReservaRepository repository, PreenchedorReserva preenchedor, ReservaMapper mapper) {
        this.repository = repository;
        this.preenchedor = preenchedor;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse execute(UUID id, ReservaRequest request) {
        Reserva reserva = repository.findDetalhadaById(id).orElseThrow(() -> new ReservaNotFoundException(id));
        if (reserva.getStatus() != StatusReserva.RESERVADO) {
            throw new RegraDeNegocioException("Só é possível editar reservas com status RESERVADO (atual: "
                    + reserva.getStatus() + ")");
        }
        preenchedor.preencher(request, reserva);
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }
}
