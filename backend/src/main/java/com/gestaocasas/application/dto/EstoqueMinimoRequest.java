package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record EstoqueMinimoRequest(
        @NotNull(message = "Estoque mínimo é obrigatório")
        @PositiveOrZero(message = "Estoque mínimo não pode ser negativo")
        @Max(value = 1_000_000, message = "Estoque mínimo deve ser no máximo 1000000")
        Integer estoqueMinimo
) {
}
