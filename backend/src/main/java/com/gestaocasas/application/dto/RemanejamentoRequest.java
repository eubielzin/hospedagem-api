package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Nova UH e/ou novas datas. Com o hóspede na casa, o checkin não muda. */
public record RemanejamentoRequest(
        @NotNull(message = "UH é obrigatória")
        UUID uhId,

        @NotNull(message = "Data de entrada é obrigatória")
        LocalDate checkin,

        @NotNull(message = "Data de saída é obrigatória")
        LocalDate checkout
) {
}
