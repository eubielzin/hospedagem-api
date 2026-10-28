package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** taxa ausente vale zero; descricao ausente vira "Recebimento da reserva CM-xxxx". */
public record PagamentoRequest(
        @NotNull(message = "Informe a forma de pagamento")
        FormaPagamento forma,

        @NotNull(message = "Informe o valor")
        @DecimalMin(value = "0.01", message = "Informe um valor maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O valor deve ter no máximo 2 casas decimais")
        BigDecimal valorBruto,

        @PositiveOrZero(message = "A taxa não pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "A taxa deve ter no máximo 2 casas decimais")
        BigDecimal taxa,

        @Size(max = 300, message = "Descrição deve ter no máximo 300 caracteres")
        String descricao
) {
}
