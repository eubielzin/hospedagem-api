package com.gestaocasas.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Saldo de um produto em um imóvel. {@code atualizadoEm} é nulo se o imóvel nunca teve o produto. */
public record EstoqueItemResponse(
        UUID imovelId,
        UUID produtoId,
        String codigo,
        String codigoBarras,
        String nome,
        String unidade,
        boolean produtoAtivo,
        int quantidadeAtual,
        int estoqueMinimo,
        boolean abaixoDoMinimo,
        Instant atualizadoEm
) {
}
