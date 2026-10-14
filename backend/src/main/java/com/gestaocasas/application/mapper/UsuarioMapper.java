package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.domain.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UsuarioMapper {

    UsuarioResponse toResponse(Usuario usuario);
}
