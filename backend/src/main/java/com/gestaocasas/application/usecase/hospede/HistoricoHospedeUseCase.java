package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.repository.HospedeRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** RF-27: reservas em que o hóspede é o titular, da entrada mais recente para a mais antiga. */
@Component
public class HistoricoHospedeUseCase {

    private final HospedeRepository hospedeRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaMapper mapper;

    public HistoricoHospedeUseCase(HospedeRepository hospedeRepository, ReservaRepository reservaRepository,
                                   ReservaMapper mapper) {
        this.hospedeRepository = hospedeRepository;
        this.reservaRepository = reservaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> execute(UUID id) {
        if (!hospedeRepository.existsById(id)) {
            throw new HospedeNotFoundException(id);
        }
        return reservaRepository.findByTitularIdOrderByCheckinDescCodigoDesc(id).stream()
                .map(mapper::toResumo)
                .toList();
    }
}
