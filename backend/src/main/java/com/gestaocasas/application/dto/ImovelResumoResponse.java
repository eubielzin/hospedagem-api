package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusImovel;

import java.math.BigDecimal;
import java.util.UUID;

public record ImovelResumoResponse(
        UUID id,
        String nome,
        String cidade,
        String uf,
        Integer capacidadeMaxima,
        Integer quartos,
        BigDecimal valorBaseDiaria,
        StatusImovel status
) {
}
