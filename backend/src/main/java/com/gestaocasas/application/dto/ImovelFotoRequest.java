package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ImovelFotoRequest(
        @NotBlank(message = "URL é obrigatória")
        @Size(max = 1000, message = "URL deve ter no máximo 1000 caracteres")
        @Pattern(regexp = "(?i)https?://\\S+", message = "URL deve começar com http:// ou https://")
        String url,

        @Size(max = 200, message = "Descrição deve ter no máximo 200 caracteres")
        String descricao,

        @PositiveOrZero(message = "Ordem não pode ser negativa")
        Integer ordem
) {
}
