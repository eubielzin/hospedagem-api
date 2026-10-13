package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.CategoriaProduto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProdutoResponse(
        UUID id,
        String codigo,
        String codigoBarras,
        String nome,
        String descricao,
        CategoriaProduto categoria,
        String unidade,
        BigDecimal precoCusto,
        BigDecimal precoVenda,
        boolean controlaEstoque,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
