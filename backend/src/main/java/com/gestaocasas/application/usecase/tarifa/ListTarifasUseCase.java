package com.gestaocasas.application.usecase.tarifa;

import com.gestaocasas.application.dto.TarifaResponse;
import com.gestaocasas.application.mapper.CategoriaUhMapper;
import com.gestaocasas.domain.repository.TarifaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListTarifasUseCase {

    private final TarifaRepository repository;
    private final CategoriaUhMapper mapper;

    public ListTarifasUseCase(TarifaRepository repository, CategoriaUhMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TarifaResponse> execute() {
        return repository.findAllOrdenadas().stream().map(mapper::toResponse).toList();
    }
}
