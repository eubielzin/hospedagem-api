package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.FormaPagamento;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoVenda;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendaResumoResponse(
        UUID id,
        UUID imovelId,
        TipoVenda tipo,
        StatusVenda status,
        StatusPagamento pagamentoStatus,
        BigDecimal total,
        Instant criadoEm,
        Instant canceladoEm,
        FormaPagamento formaPagamento
) {
}
