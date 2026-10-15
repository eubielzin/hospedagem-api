package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapper;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateHospedeUseCase {

    private final HospedeRepository repository;
    private final HospedeMapper mapper;
    private final HospedeDocumentoValidator documento;

    public CreateHospedeUseCase(HospedeRepository repository, HospedeMapper mapper,
                                HospedeDocumentoValidator documento) {
        this.repository = repository;
        this.mapper = mapper;
        this.documento = documento;
    }

    @Transactional
    public HospedeResponse execute(HospedeRequest request) {
        documento.validar(request.tipoDocumento(), mapper.documento(request), null);
        Hospede hospede = new Hospede();
        mapper.updateEntity(request, hospede);
        hospede.setAtivo(true);
        return mapper.toResponse(repository.saveAndFlush(hospede));
    }
}
