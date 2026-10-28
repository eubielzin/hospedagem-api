package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Com produtoId, o preço é o do cadastro (valorUnitario é ignorado) e a descrição, se vazia, é o nome do
 * produto. Sem produto, descrição e valorUnitario são obrigatórios.
 */
public record LancamentoRequest(
        @Size(max = 150, message = "Descrição deve ter no máximo 150 caracteres")
        String descricao,

        UUID produtoId,

        @NotNull(message = "Informe a quantidade")
        @Min(value = 1, message = "A quantidade deve ser de pelo menos 1")
        @Max(value = 10000, message = "A quantidade deve ser de no máximo 10000")
        Integer quantidade,

        @PositiveOrZero(message = "O valor não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        BigDecimal valorUnitario
) {
}
