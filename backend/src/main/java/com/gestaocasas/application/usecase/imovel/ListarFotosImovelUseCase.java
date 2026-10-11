package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListarFotosImovelUseCase {

    private final ImovelRepository imovelRepository;
    private final ImovelFotoRepository fotoRepository;
    private final ImovelMapper mapper;

    public ListarFotosImovelUseCase(ImovelRepository imovelRepository, ImovelFotoRepository fotoRepository,
                                    ImovelMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.fotoRepository = fotoRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ImovelFotoResponse> execute(UUID imovelId) {
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        return fotoRepository.findByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId)
                .stream().map(mapper::toResponse).toList();
    }
}
