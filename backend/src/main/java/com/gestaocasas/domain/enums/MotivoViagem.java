package com.gestaocasas.domain.enums;

/** Motivo da viagem na FNRH. */
public enum MotivoViagem {
    LAZER("Lazer/férias"),
    NEGOCIOS("Negócios"),
    CONGRESSO("Congresso/feira"),
    PARENTES("Parentes/amigos"),
    ESTUDOS("Estudos"),
    SAUDE("Saúde"),
    RELIGIAO("Religião"),
    OUTRO("Outro");

    private final String rotulo;

    MotivoViagem(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
