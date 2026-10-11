package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DefinirFotoPrincipalUseCase {

    private final ImovelRepository imovelRepository;
    private final ImovelFotoRepository fotoRepository;
    private final ImovelMapper mapper;

    public DefinirFotoPrincipalUseCase(ImovelRepository imovelRepository, ImovelFotoRepository fotoRepository,
                                       ImovelMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.fotoRepository = fotoRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ImovelFotoResponse execute(UUID imovelId, UUID fotoId) {
        imovelRepository.findByIdForUpdate(imovelId).orElseThrow(() -> new ImovelNotFoundException(imovelId));
        // Desmarca a atual antes de carregar a nova, para a entidade refletir o estado gravado.
        fotoRepository.desmarcarPrincipal(imovelId);
        ImovelFoto foto = fotoRepository.findByIdAndImovelIdAndExclusaoPendenteEmIsNull(fotoId, imovelId)
                .orElseThrow(() -> new ImovelFotoNotFoundException(fotoId));
        foto.setPrincipal(true);
        return mapper.toResponse(fotoRepository.saveAndFlush(foto));
    }
}
