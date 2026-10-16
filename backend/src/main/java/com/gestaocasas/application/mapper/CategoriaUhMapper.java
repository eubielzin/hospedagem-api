package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.CategoriaUhResponse;
import com.gestaocasas.application.dto.TarifaResponse;
import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.entity.Tarifa;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CategoriaUhMapper {

    CategoriaUhResponse toResponse(CategoriaUh categoria);

    @Mapping(target = "categoriaId", source = "categoria.id")
    TarifaResponse toResponse(Tarifa tarifa);
}
