package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** uhId é o id do imóvel. */
public record ReservaRequest(
        @NotNull(message = "UH é obrigatória")
        UUID uhId,

        @NotNull(message = "Hóspede titular é obrigatório")
        UUID titularId,

        @NotNull(message = "Canal é obrigatório")
        UUID canalId,

        @NotNull(message = "Data de entrada é obrigatória")
        LocalDate checkin,

        @NotNull(message = "Data de saída é obrigatória")
        LocalDate checkout,

        @NotNull(message = "Informe o número de adultos")
        @Min(value = 1, message = "A reserva precisa de ao menos 1 adulto")
        Integer adultos,

        @Min(value = 0, message = "Crianças não pode ser negativo")
        Integer criancas,

        @NotNull(message = "Valor total é obrigatório")
        @PositiveOrZero(message = "Valor total não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Valor total deve ter no máximo 2 casas decimais")
        BigDecimal valorTotal,

        @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres")
        String observacoes
) {
}
