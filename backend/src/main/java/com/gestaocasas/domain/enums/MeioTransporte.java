package com.gestaocasas.domain.enums;

/** Meio de transporte na FNRH. */
public enum MeioTransporte {
    AVIAO("Avião"),
    CARRO("Automóvel"),
    ONIBUS("Ônibus"),
    MOTO("Moto"),
    NAVIO("Navio/barco"),
    OUTRO("Outro");

    private final String rotulo;

    MeioTransporte(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
