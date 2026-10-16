package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class UpdateImovelUseCase {

    private final ImovelRepository repository;
    private final ImovelMapper mapper;
    private final PreenchedorImovel preenchedor;

    public UpdateImovelUseCase(ImovelRepository repository, ImovelMapper mapper, PreenchedorImovel preenchedor) {
        this.repository = repository;
        this.mapper = mapper;
        this.preenchedor = preenchedor;
    }

    @Transactional
    public ImovelResponse execute(UUID id, ImovelRequest request) {
        Imovel imovel = repository.findByIdForUpdate(id).orElseThrow(() -> new ImovelNotFoundException(id));
        preenchedor.preencher(request, imovel);
        return mapper.toResponse(repository.saveAndFlush(imovel));
    }
}
