package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;

import java.time.Instant;
import java.util.UUID;

public record MovimentacaoEstoqueResponse(
        UUID id,
        UUID imovelId,
        UUID produtoId,
        String produtoCodigo,
        String produtoNome,
        TipoMovimentacao tipo,
        int quantidade,
        int saldoAnterior,
        int saldoPosterior,
        String motivo,
        TipoReferenciaMovimentacao referenciaTipo,
        UUID referenciaId,
        Instant criadoEm
) {
}
