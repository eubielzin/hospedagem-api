package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.CanalResponse;
import com.gestaocasas.domain.entity.Canal;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CanalMapper {

    CanalResponse toResponse(Canal canal);
}
