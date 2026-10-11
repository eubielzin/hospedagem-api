package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetImovelByIdUseCase {

    private final ImovelRepository repository;
    private final ImovelMapper mapper;

    public GetImovelByIdUseCase(ImovelRepository repository, ImovelMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ImovelResponse execute(UUID id) {
        return repository.findById(id).map(mapper::toResponse).orElseThrow(() -> new ImovelNotFoundException(id));
    }
}
