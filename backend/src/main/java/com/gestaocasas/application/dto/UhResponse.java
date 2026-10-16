package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.TipoUh;

import java.util.UUID;

/** O imóvel visto como UH. statusGovernanca já vem calculado (OCUPADA, BLOQUEADA). */
public record UhResponse(
        UUID id,
        String nome,
        TipoUh tipo,
        UUID categoriaId,
        int capacidadeAdultos,
        int capacidadeCriancas,
        StatusGovernanca statusGovernanca,
        boolean ativa
) {
}
