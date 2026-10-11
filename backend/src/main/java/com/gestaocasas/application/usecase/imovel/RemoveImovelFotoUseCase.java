package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class RemoveImovelFotoUseCase {

    private final ImovelFotoRepository fotoRepository;

    public RemoveImovelFotoUseCase(ImovelFotoRepository fotoRepository) {
        this.fotoRepository = fotoRepository;
    }

    @Transactional
    public void execute(UUID imovelId, UUID fotoId) {
        ImovelFoto foto = fotoRepository.findByIdAndImovelId(fotoId, imovelId)
                .orElseThrow(() -> new ImovelFotoNotFoundException(fotoId));
        fotoRepository.delete(foto);
    }
}
