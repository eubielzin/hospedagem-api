package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecebimentoRepasseRequest(
        @NotNull(message = "Informe a data do recebimento")
        LocalDate data
) {
}
