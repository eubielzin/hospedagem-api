package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.HospedeRequest;
import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.enums.TipoDocumento;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Locale;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HospedeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "numeroDocumento", ignore = true)
    @Mapping(target = "nome", qualifiedByName = "texto")
    @Mapping(target = "nacionalidade", qualifiedByName = "texto")
    @Mapping(target = "telefone", qualifiedByName = "texto")
    void updateEntity(HospedeRequest request, @MappingTarget Hospede hospede);

    /** CPF guardado só com os dígitos; RG e passaporte em maiúsculas. Sem documento, texto vazio. */
    @AfterMapping
    default void copiarDocumento(HospedeRequest request, @MappingTarget Hospede hospede) {
        hospede.setNumeroDocumento(documento(request));
    }

    default String documento(HospedeRequest request) {
        String numero = request.numeroDocumento() == null ? "" : request.numeroDocumento().trim();
        return request.tipoDocumento() == TipoDocumento.CPF
                ? numero.replaceAll("\\D", "")
                : numero.toUpperCase(Locale.ROOT);
    }

    HospedeResponse toResponse(Hospede hospede);

    /** Remove espaços das pontas; texto em branco vira ausência de valor. */
    @Named("texto")
    default String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
