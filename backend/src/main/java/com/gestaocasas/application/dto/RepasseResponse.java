package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** RN-09: comissao = valorBruto × comissão do canal (%); previsao = checkout (premissa a validar por OTA). */
public record RepasseResponse(UUID reservaId, String codigoReserva, String canalNome, BigDecimal valorBruto,
                              BigDecimal comissao, BigDecimal valorLiquido, LocalDate previsao, LocalDate recebidoEm) {
}
