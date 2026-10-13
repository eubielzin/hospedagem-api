package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoAjusteEstoque;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AjusteEstoqueRequest(
        @NotNull(message = "Produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "Tipo é obrigatório")
        TipoAjusteEstoque tipo,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        @Max(value = 1_000_000, message = "Quantidade deve ser no máximo 1000000")
        Integer quantidade,

        @NotBlank(message = "Motivo é obrigatório em ajustes e perdas")
        @Size(max = 300, message = "Motivo deve ter no máximo 300 caracteres")
        String motivo
) {
}
