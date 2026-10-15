package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapper;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ReativarHospedeUseCase {

    private final HospedeRepository repository;
    private final HospedeMapper mapper;

    public ReativarHospedeUseCase(HospedeRepository repository, HospedeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public HospedeResponse execute(UUID id) {
        Hospede hospede = repository.findById(id).orElseThrow(() -> new HospedeNotFoundException(id));
        hospede.setAtivo(true);
        return mapper.toResponse(repository.saveAndFlush(hospede));
    }
}
