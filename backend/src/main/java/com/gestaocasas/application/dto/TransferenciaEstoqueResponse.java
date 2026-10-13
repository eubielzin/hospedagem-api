package com.gestaocasas.application.dto;

import java.util.UUID;

/** As duas movimentações de uma transferência compartilham o {@code transferenciaId} como referência. */
public record TransferenciaEstoqueResponse(
        UUID transferenciaId,
        MovimentacaoEstoqueResponse saida,
        MovimentacaoEstoqueResponse entrada
) {
}
