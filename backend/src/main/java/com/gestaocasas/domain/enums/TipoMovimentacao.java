package com.gestaocasas.domain.enums;

public enum TipoMovimentacao {
    ENTRADA(true),
    SAIDA_VENDA(false),
    PERDA(false),
    AJUSTE_POSITIVO(true),
    AJUSTE_NEGATIVO(false),
    DEVOLUCAO(true),
    TRANSFERENCIA_ENTRADA(true),
    TRANSFERENCIA_SAIDA(false);

    private final boolean aumentaSaldo;

    TipoMovimentacao(boolean aumentaSaldo) {
        this.aumentaSaldo = aumentaSaldo;
    }

    public boolean aumentaSaldo() {
        return aumentaSaldo;
    }
}
