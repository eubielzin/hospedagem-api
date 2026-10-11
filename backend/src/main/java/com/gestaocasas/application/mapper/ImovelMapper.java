package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.dto.ImovelResumoResponse;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ImovelFoto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ImovelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "comodidades", ignore = true)
    @Mapping(target = "fotos", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "uf", expression = "java(request.uf().toUpperCase())")
    void updateEntity(ImovelRequest request, @MappingTarget Imovel imovel);

    // Substitui o conteúdo da coleção gerenciada pelo Hibernate em vez de trocar a instância.
    @AfterMapping
    default void copyComodidades(ImovelRequest request, @MappingTarget Imovel imovel) {
        imovel.getComodidades().clear();
        if (request.comodidades() != null) {
            request.comodidades().stream().map(String::trim).forEach(imovel.getComodidades()::add);
        }
    }

    ImovelResponse toResponse(Imovel imovel);

    ImovelResumoResponse toResumo(Imovel imovel);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imovel", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "storageProvider", ignore = true)
    @Mapping(target = "storagePath", ignore = true)
    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "exclusaoPendenteEm", ignore = true)
    ImovelFoto toEntity(ImovelFotoRequest request);

    ImovelFotoResponse toResponse(ImovelFoto foto);
}
