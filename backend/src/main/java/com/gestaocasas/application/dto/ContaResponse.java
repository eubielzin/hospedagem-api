package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RF-43. saldo = diarias − repasseCanal + consumos − pagamentos. Em reservas de OTA as diárias são crédito a
 * receber do canal (RN-09) e ficam fora do saldo do hóspede.
 */
public record ContaResponse(UUID reservaId, BigDecimal diarias, BigDecimal repasseCanal, BigDecimal consumos,
                            BigDecimal pagamentos, BigDecimal saldo, List<LancamentoResponse> lancamentos,
                            List<PagamentoResponse> recebimentos) {
}
