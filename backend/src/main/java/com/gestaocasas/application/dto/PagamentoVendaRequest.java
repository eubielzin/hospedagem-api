package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.FormaPagamento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * O valor pago é sempre o total da venda menos o devolvido, calculado pelo servidor. valorRecebido só em
 * dinheiro (obrigatório nele), para o troco. taxa ausente vale zero.
 */
public record PagamentoVendaRequest(
        @NotNull(message = "Informe a forma de pagamento")
        FormaPagamento formaPagamento,

        @PositiveOrZero(message = "O valor recebido não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "O valor recebido deve ter no máximo 2 casas decimais")
        BigDecimal valorRecebido,

        @PositiveOrZero(message = "A taxa não pode ser negativa")
        @Digits(integer = 10, fraction = 2, message = "A taxa deve ter no máximo 2 casas decimais")
        BigDecimal taxa,

        @Size(max = 300, message = "Observação deve ter no máximo 300 caracteres")
        String observacao
) {
}
