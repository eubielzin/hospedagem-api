package com.gestaocasas.domain.enums;

public enum StatusReserva {
    RESERVADO,
    HOSPEDADO,
    CHECKOUT_REALIZADO,
    CANCELADA,
    /** Previsto no contrato do frontend; ainda não há rota que registre no-show. */
    NO_SHOW;

    /** Ocupam o imóvel no período: bloqueiam outras reservas. */
    public boolean ocupaImovel() {
        return this == RESERVADO || this == HOSPEDADO;
    }
}
