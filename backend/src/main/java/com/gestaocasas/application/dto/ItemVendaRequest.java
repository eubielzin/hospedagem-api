package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Não há campo de preço: o preço de cada item é sempre o do cadastro, lido pelo backend. */
public record ItemVendaRequest(
        @NotNull(message = "Produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        @Max(value = 1_000_000, message = "Quantidade deve ser no máximo 1000000")
        Integer quantidade
) {
}
