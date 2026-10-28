package com.gestaocasas.domain.enums;

public enum FormaPagamento {
    PIX("Pix"),
    CREDITO("Cartão de crédito"),
    DEBITO("Cartão de débito"),
    DINHEIRO("Dinheiro"),
    OUTRO("Outro");

    private final String rotulo;

    FormaPagamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
