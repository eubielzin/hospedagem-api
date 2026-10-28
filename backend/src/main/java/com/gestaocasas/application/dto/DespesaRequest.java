package com.gestaocasas.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaRequest(
        @NotBlank(message = "Informe a categoria")
        @Size(max = 80, message = "Categoria deve ter no máximo 80 caracteres")
        String categoria,

        @NotBlank(message = "Informe o fornecedor")
        @Size(max = 120, message = "Fornecedor deve ter no máximo 120 caracteres")
        String fornecedor,

        @Size(max = 300, message = "Descrição deve ter no máximo 300 caracteres")
        String descricao,

        @NotNull(message = "Informe o valor da despesa")
        @DecimalMin(value = "0.01", message = "Informe o valor da despesa")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        BigDecimal valor,

        @NotNull(message = "Informe o vencimento")
        LocalDate vencimento
) {
}
