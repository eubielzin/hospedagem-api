package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EntradaEstoqueRequest(
        @NotNull(message = "Produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        @Max(value = 1_000_000, message = "Quantidade deve ser no máximo 1000000")
        Integer quantidade,

        @Size(max = 300, message = "Motivo deve ter no máximo 300 caracteres")
        String motivo
) {
}
