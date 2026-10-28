package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Cancelamento ou no-show. Valores ausentes valem zero; a política de multa ainda está a validar. */
public record EncerramentoRequest(
        @NotBlank(message = "Informe o motivo")
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        String motivo,

        @PositiveOrZero(message = "A multa não pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "A multa deve ter no máximo 2 casas decimais")
        BigDecimal multa,

        @PositiveOrZero(message = "O valor retido não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O valor retido deve ter no máximo 2 casas decimais")
        BigDecimal valorRetido,

        @PositiveOrZero(message = "O valor a devolver não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O valor a devolver deve ter no máximo 2 casas decimais")
        BigDecimal valorDevolver
) {
}
