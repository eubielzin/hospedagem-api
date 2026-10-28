package com.gestaocasas.domain.enums;

/** RF-52. As refeições de cada regime ainda estão a validar com a pousada. */
public enum RegimePensao {
    SEM_REFEICOES("Sem refeições"),
    CAFE_DA_MANHA("Café da manhã"),
    MEIA_PENSAO("Meia pensão"),
    PENSAO_COMPLETA("Pensão completa");

    private final String rotulo;

    RegimePensao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
