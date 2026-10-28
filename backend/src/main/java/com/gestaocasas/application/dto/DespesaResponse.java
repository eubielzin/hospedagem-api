package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DespesaResponse(UUID id, String categoria, String fornecedor, String descricao, BigDecimal valor,
                              LocalDate vencimento, LocalDate dataPagamento, String comprovante) {
}
