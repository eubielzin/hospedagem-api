package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.CategoriaProduto;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank(message = "Código é obrigatório")
        @Size(max = 40, message = "Código deve ter no máximo 40 caracteres")
        String codigo,

        @Size(max = 50, message = "Código de barras deve ter no máximo 50 caracteres")
        String codigoBarras,

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres")
        String descricao,

        @NotNull(message = "Categoria é obrigatória")
        CategoriaProduto categoria,

        @NotBlank(message = "Unidade é obrigatória")
        @Size(max = 10, message = "Unidade deve ter no máximo 10 caracteres")
        String unidade,

        @PositiveOrZero(message = "Preço de custo não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Preço de custo deve ter no máximo 2 casas decimais")
        BigDecimal precoCusto,

        @NotNull(message = "Preço de venda é obrigatório")
        @PositiveOrZero(message = "Preço de venda não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Preço de venda deve ter no máximo 2 casas decimais")
        BigDecimal precoVenda,

        @NotNull(message = "Informe se o produto controla estoque")
        Boolean controlaEstoque,

        Boolean ativo
) {
}
