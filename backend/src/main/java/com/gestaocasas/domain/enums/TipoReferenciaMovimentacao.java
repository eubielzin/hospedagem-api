package com.gestaocasas.domain.enums;

/** Operação que originou uma movimentação de estoque. */
public enum TipoReferenciaMovimentacao {
    TRANSFERENCIA,
    VENDA,
    /** Consumo lançado na conta da reserva (referenciaId = id do lançamento). */
    LANCAMENTO_CONTA
}
