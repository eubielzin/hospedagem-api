package com.gestaocasas.domain.enums;

/** RN-03: RESERVADO → HOSPEDADO → CHECKOUT_REALIZADO, ou RESERVADO → CANCELADA / NO_SHOW. */
public enum StatusReserva {
    RESERVADO("Reservado"),
    HOSPEDADO("Hospedado"),
    CHECKOUT_REALIZADO("Check-out realizado"),
    CANCELADA("Cancelada"),
    NO_SHOW("No-show");

    private final String rotulo;

    StatusReserva(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Cancelada e no-show encerram a reserva e liberam a UH; as demais ocupam o período. */
    public boolean ativa() {
        return this != CANCELADA && this != NO_SHOW;
    }
}
