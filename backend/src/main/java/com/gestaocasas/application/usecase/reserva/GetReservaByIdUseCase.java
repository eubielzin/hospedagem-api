package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetReservaByIdUseCase {

    private final ReservaRepository repository;
    private final ReservaMapper mapper;

    public GetReservaByIdUseCase(ReservaRepository repository, ReservaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ReservaResponse execute(UUID id) {
        return repository.findDetalhadaById(id).map(mapper::toResponse)
                .orElseThrow(() -> new ReservaNotFoundException(id));
    }
}
