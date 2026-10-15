package com.gestaocasas.application.usecase.canal;

import com.gestaocasas.application.dto.CanalResponse;
import com.gestaocasas.application.mapper.CanalMapper;
import com.gestaocasas.domain.repository.CanalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListCanaisUseCase {

    private final CanalRepository repository;
    private final CanalMapper mapper;

    public ListCanaisUseCase(CanalRepository repository, CanalMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CanalResponse> execute(boolean incluirInativos) {
        return (incluirInativos ? repository.findAllByOrderByNome() : repository.findByAtivoTrueOrderByNome())
                .stream().map(mapper::toResponse).toList();
    }
}
