package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetVendaByIdUseCase {

    private final VendaRepository repository;
    private final VendaMapper mapper;

    public GetVendaByIdUseCase(VendaRepository repository, VendaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public VendaResponse execute(UUID id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> new VendaNotFoundException(id));
    }
}
