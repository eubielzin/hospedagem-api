package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoCanal;

import java.util.UUID;

public record CanalResponse(UUID id, String nome, TipoCanal tipo, boolean ativo) {
}
