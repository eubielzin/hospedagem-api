package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;

import java.math.BigDecimal;
import java.util.UUID;

public record ImovelResumoResponse(
        UUID id,
        String nome,
        String cidade,
        String uf,
        TipoUh tipo,
        Integer capacidadeMaxima,
        Integer quartos,
        BigDecimal valorBaseDiaria,
        StatusImovel status
) {
}
