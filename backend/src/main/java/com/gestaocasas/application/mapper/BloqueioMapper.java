package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.BloqueioResponse;
import com.gestaocasas.domain.entity.Bloqueio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BloqueioMapper {

    @Mapping(target = "uhId", source = "imovel.id")
    @Mapping(target = "uhNome", source = "imovel.nome")
    @Mapping(target = "usuario", source = "usuario.nome")
    BloqueioResponse toResponse(Bloqueio bloqueio);
}
