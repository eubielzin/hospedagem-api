package com.gestaocasas.domain.enums;

/** Correções manuais de saldo aceitas em um ajuste de inventário. */
public enum TipoAjusteEstoque {
    AJUSTE_POSITIVO(TipoMovimentacao.AJUSTE_POSITIVO),
    AJUSTE_NEGATIVO(TipoMovimentacao.AJUSTE_NEGATIVO),
    PERDA(TipoMovimentacao.PERDA);

    private final TipoMovimentacao movimentacao;

    TipoAjusteEstoque(TipoMovimentacao movimentacao) {
        this.movimentacao = movimentacao;
    }

    public TipoMovimentacao movimentacao() {
        return movimentacao;
    }
}
