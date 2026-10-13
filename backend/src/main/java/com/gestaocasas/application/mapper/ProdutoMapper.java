package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.domain.entity.Produto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Locale;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProdutoMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ativo", ignore = true)
    @Mapping(target = "criadoEm", ignore = true)
    @Mapping(target = "atualizadoEm", ignore = true)
    @Mapping(target = "codigo", qualifiedByName = "texto")
    @Mapping(target = "codigoBarras", qualifiedByName = "texto")
    @Mapping(target = "nome", qualifiedByName = "texto")
    @Mapping(target = "descricao", qualifiedByName = "texto")
    @Mapping(target = "unidade", qualifiedByName = "unidade")
    void updateEntity(ProdutoRequest request, @MappingTarget Produto produto);

    ProdutoResponse toResponse(Produto produto);

    /** Remove espaços das pontas; texto em branco vira ausência de valor. */
    @Named("texto")
    static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Named("unidade")
    static String unidade(String valor) {
        String texto = texto(valor);
        return texto == null ? null : texto.toUpperCase(Locale.ROOT);
    }
}
