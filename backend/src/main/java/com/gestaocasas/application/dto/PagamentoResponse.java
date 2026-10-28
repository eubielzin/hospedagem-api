package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** reservaId nulo: pagamento de venda avulsa do PDV (vendaId). usuario é o nome de quem registrou. */
public record PagamentoResponse(UUID id, UUID reservaId, UUID vendaId, LocalDate data, FormaPagamento forma,
                                BigDecimal valorBruto, BigDecimal taxa, BigDecimal valorLiquido, String usuario,
                                String descricao) {
}
