package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.domain.entity.Reserva;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReservaMapper {

    @Mapping(target = "uhId", source = "imovel.id")
    @Mapping(target = "uhNome", source = "imovel.nome")
    @Mapping(target = "titularId", source = "titular.id")
    @Mapping(target = "titularNome", source = "titular.nome")
    @Mapping(target = "canalId", source = "canal.id")
    @Mapping(target = "canalNome", source = "canal.nome")
    @Mapping(target = "criadaEm", source = "criadoEm")
    ReservaResponse toResponse(Reserva reserva);
}
