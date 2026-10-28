package com.gestaocasas.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Saldo da conta de uma reserva, calculado no banco. */
public interface SaldoReserva {

    UUID getReservaId();

    BigDecimal getSaldo();
}
