package com.gestaocasas.application.usecase.uh;

import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** RF-34 e RF-35: a governança marca a UH como livre, suja ou em manutenção. */
@Component
public class AlterarGovernancaUseCase {

    private final ImovelRepository repository;
    private final GovernancaUh governanca;

    public AlterarGovernancaUseCase(ImovelRepository repository, GovernancaUh governanca) {
        this.repository = repository;
        this.governanca = governanca;
    }

    @Transactional
    public UhResponse execute(UUID id, StatusGovernanca status) {
        Imovel imovel = repository.findByIdForUpdate(id).orElseThrow(() -> new ImovelNotFoundException(id));
        governanca.definir(imovel, status);
        return governanca.toResponse(repository.saveAndFlush(imovel));
    }
}
