package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVendaResponse(
        UUID id,
        UUID produtoId,
        String produtoCodigo,
        String produtoNome,
        int quantidade,
        int quantidadeDevolvida,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {
}
