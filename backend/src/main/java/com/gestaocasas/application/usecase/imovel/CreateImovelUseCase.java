package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateImovelUseCase {

    private final ImovelRepository repository;
    private final ImovelMapper mapper;

    public CreateImovelUseCase(ImovelRepository repository, ImovelMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public ImovelResponse execute(ImovelRequest request) {
        Imovel imovel = new Imovel();
        mapper.updateEntity(request, imovel);
        imovel.setStatus(request.status() != null ? request.status() : StatusImovel.ATIVO);
        return mapper.toResponse(repository.saveAndFlush(imovel));
    }
}
