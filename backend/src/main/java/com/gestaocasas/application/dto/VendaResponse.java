package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.FormaPagamento;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoVenda;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code totalDevolvido} é o valor dos itens devolvidos; não indica que houve reembolso. Os campos do pagamento
 * (formaPagamento a pagoEm) vêm nulos enquanto pagamentoStatus for PENDENTE; valorRecebido e troco, só em dinheiro.
 */
public record VendaResponse(
        UUID id,
        UUID imovelId,
        TipoVenda tipo,
        StatusVenda status,
        StatusPagamento pagamentoStatus,
        BigDecimal total,
        BigDecimal totalDevolvido,
        String observacao,
        List<ItemVendaResponse> itens,
        Instant criadoEm,
        Instant canceladoEm,
        String motivoCancelamento,
        FormaPagamento formaPagamento,
        BigDecimal valorPago,
        BigDecimal valorRecebido,
        BigDecimal troco,
        Instant pagoEm
) {
}
