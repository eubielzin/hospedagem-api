package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LancamentoResponse(UUID id, UUID reservaId, LocalDate data, String descricao, UUID produtoId,
                                 int quantidade, BigDecimal valorUnitario, BigDecimal total, boolean estornado,
                                 String motivoEstorno) {
}
