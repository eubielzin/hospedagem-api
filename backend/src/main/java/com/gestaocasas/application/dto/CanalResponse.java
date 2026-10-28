package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoCanal;

import java.math.BigDecimal;
import java.util.UUID;

/** comissao em %; cor "#rrggbb". */
public record CanalResponse(UUID id, String nome, TipoCanal tipo, BigDecimal comissao, String cor, boolean ativo) {
}
