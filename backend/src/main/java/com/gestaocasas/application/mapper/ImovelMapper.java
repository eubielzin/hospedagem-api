package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.dto.ImovelResumoResponse;
import com.gestaocasas.application.service.HorariosPadrao;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ImovelFoto;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Copia só os dados de cadastro. Tipo, categoria, capacidades, status e governança têm regras próprias e
 * ficam com o {@code PreenchedorImovel}.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class ImovelMapper {

    protected HorariosPadrao horariosPadrao;

    @Autowired
    public void setHorariosPadrao(HorariosPadrao horariosPadrao) {
        this.horariosPadrao = horariosPadrao;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tipo", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "capacidadeAdultos", ignore = true)
    @Mapping(target = "capacidadeCriancas", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusGovernanca", ignore = true)
    @Mapping(target = "comodidades", ignore = true)
    @Mapping(target = "fotos", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "uf", expression = "java(request.uf() == null ? null : request.uf().toUpperCase())")
    public abstract void updateEntity(ImovelRequest request, @MappingTarget Imovel imovel);

    // Substitui o conteúdo da coleção gerenciada pelo Hibernate em vez de trocar a instância.
    @AfterMapping
    protected void copyComodidades(ImovelRequest request, @MappingTarget Imovel imovel) {
        imovel.getComodidades().clear();
        if (request.comodidades() != null) {
            request.comodidades().stream().map(String::trim).forEach(imovel.getComodidades()::add);
        }
    }

    @Mapping(target = "horarioCheckin", expression = "java(horariosPadrao.checkin(imovel))")
    @Mapping(target = "horarioCheckout", expression = "java(horariosPadrao.checkout(imovel))")
    public abstract ImovelResponse toResponse(Imovel imovel);

    public abstract ImovelResumoResponse toResumo(Imovel imovel);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imovel", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "storageProvider", ignore = true)
    @Mapping(target = "storagePath", ignore = true)
    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "exclusaoPendenteEm", ignore = true)
    public abstract ImovelFoto toEntity(ImovelFotoRequest request);

    public abstract ImovelFotoResponse toResponse(ImovelFoto foto);
}
