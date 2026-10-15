package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapper;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetHospedeByIdUseCase {

    private final HospedeRepository repository;
    private final HospedeMapper mapper;

    public GetHospedeByIdUseCase(HospedeRepository repository, HospedeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public HospedeResponse execute(UUID id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> new HospedeNotFoundException(id));
    }
}
