package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateReservaUseCase {

    private final ReservaRepository repository;
    private final PreenchedorReserva preenchedor;
    private final ReservaMapper mapper;

    public CreateReservaUseCase(ReservaRepository repository, PreenchedorReserva preenchedor, ReservaMapper mapper) {
        this.repository = repository;
        this.preenchedor = preenchedor;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse execute(ReservaRequest request) {
        Reserva reserva = new Reserva();
        preenchedor.preencher(request, reserva);
        reserva.setStatus(StatusReserva.RESERVADO);
        reserva.setCodigo("CM-" + repository.proximoNumero());
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }
}
