package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.application.service.CotadorReserva;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/** RF-31 e RF-32: o valor que a reserva teria, sem conferir disponibilidade nem capacidade. */
@Component
public class CotarReservaUseCase {

    private final ImovelRepository imovelRepository;
    private final CotadorReserva cotador;

    public CotarReservaUseCase(ImovelRepository imovelRepository, CotadorReserva cotador) {
        this.imovelRepository = imovelRepository;
        this.cotador = cotador;
    }

    @Transactional(readOnly = true)
    public CotacaoResponse execute(UUID uhId, LocalDate checkin, LocalDate checkout) {
        return cotador.cotar(imovelRepository.findById(uhId).orElseThrow(() -> new ImovelNotFoundException(uhId)),
                checkin, checkout);
    }
}
