package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.CategoriaProduto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resultado da busca dinâmica. {@code quantidadeDisponivel} é o saldo no imóvel pesquisado e vem
 * nula quando nenhum imóvel foi informado ou quando o produto não controla estoque.
 * {@code semEstoque} indica produto físico zerado naquele imóvel.
 */
public record ProdutoBuscaResponse(
        UUID id,
        String codigo,
        String codigoBarras,
        String nome,
        CategoriaProduto categoria,
        String unidade,
        BigDecimal precoVenda,
        boolean controlaEstoque,
        boolean ativo,
        Integer quantidadeDisponivel,
        boolean semEstoque
) {
}
