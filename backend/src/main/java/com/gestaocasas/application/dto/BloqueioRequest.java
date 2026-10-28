package com.gestaocasas.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** fim é o último dia bloqueado (inclusive). */
public record BloqueioRequest(
        @NotNull(message = "UH é obrigatória")
        UUID uhId,

        @NotNull(message = "Informe o início")
        LocalDate inicio,

        @NotNull(message = "Informe o fim")
        LocalDate fim,

        @NotBlank(message = "Informe o motivo do bloqueio")
        @Size(max = 300, message = "Motivo deve ter no máximo 300 caracteres")
        String motivo
) {

    @AssertTrue(message = "O fim deve ser igual ou depois do início")
    public boolean isFim() {
        return inicio == null || fim == null || !fim.isBefore(inicio);
    }
}
