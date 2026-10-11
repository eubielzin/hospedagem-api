package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AddImovelFotoUseCase {

    private final ImovelRepository imovelRepository;
    private final ImovelFotoRepository fotoRepository;
    private final ImovelMapper mapper;

    public AddImovelFotoUseCase(ImovelRepository imovelRepository, ImovelFotoRepository fotoRepository,
                                ImovelMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.fotoRepository = fotoRepository;
        this.mapper = mapper;
    }

    @Transactional
    public ImovelFotoResponse execute(UUID imovelId, ImovelFotoRequest request) {
        Imovel imovel = imovelRepository.findById(imovelId).orElseThrow(() -> new ImovelNotFoundException(imovelId));
        ImovelFoto foto = mapper.toEntity(request);
        foto.setImovel(imovel);
        if (foto.getOrdem() == null) {
            foto.setOrdem((int) fotoRepository.countByImovelId(imovelId));
        }
        return mapper.toResponse(fotoRepository.save(foto));
    }
}
