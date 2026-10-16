package com.gestaocasas.domain.enums;

/**
 * Situação da UH para a governança (RF-34). Só LIVRE, SUJA e MANUTENCAO são gravadas; OCUPADA
 * (reserva hospedada) e BLOQUEADA (bloqueio vigente) são calculadas na leitura.
 */
public enum StatusGovernanca {
    LIVRE,
    OCUPADA,
    SUJA,
    MANUTENCAO,
    BLOQUEADA;

    /** Pode ser definido pela governança e gravado no imóvel. */
    public boolean manual() {
        return this == LIVRE || this == SUJA || this == MANUTENCAO;
    }
}
