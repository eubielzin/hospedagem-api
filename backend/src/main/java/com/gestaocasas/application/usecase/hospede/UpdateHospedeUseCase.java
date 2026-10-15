package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapper;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Atualiza os dados cadastrais; a situação (ativo) não muda por aqui. */
@Component
public class UpdateHospedeUseCase {

    private final HospedeRepository repository;
    private final HospedeMapper mapper;
    private final HospedeDocumentoValidator documento;

    public UpdateHospedeUseCase(HospedeRepository repository, HospedeMapper mapper,
                                HospedeDocumentoValidator documento) {
        this.repository = repository;
        this.mapper = mapper;
        this.documento = documento;
    }

    @Transactional
    public HospedeResponse execute(UUID id, HospedeRequest request) {
        Hospede hospede = repository.findById(id).orElseThrow(() -> new HospedeNotFoundException(id));
        documento.validar(request.tipoDocumento(), mapper.documento(request), id);
        mapper.updateEntity(request, hospede);
        return mapper.toResponse(repository.saveAndFlush(hospede));
    }
}
