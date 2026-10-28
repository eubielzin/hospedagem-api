package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** status em texto legível ("Hospedado", "Check-out realizado"). */
public record PendenciaResponse(UUID reservaId, String codigoReserva, String titularNome, String uhNome, String status,
                                BigDecimal saldo) {
}
