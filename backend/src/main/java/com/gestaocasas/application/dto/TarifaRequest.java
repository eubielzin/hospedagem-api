package com.gestaocasas.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** fim é a última noite (inclusive) em que a tarifa vale. */
public record TarifaRequest(
        @NotNull(message = "Informe a categoria")
        UUID categoriaId,

        @NotBlank(message = "Informe o nome da tarifa")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String nome,

        @NotNull(message = "Informe o início")
        LocalDate inicio,

        @NotNull(message = "Informe o fim")
        LocalDate fim,

        @NotNull(message = "Informe o valor da diária")
        @DecimalMin(value = "0.01", message = "Informe o valor da diária")
        @Digits(integer = 10, fraction = 2, message = "Valor da diária deve ter no máximo 2 casas decimais")
        BigDecimal valorDiaria,

        @NotNull(message = "Informe a estadia mínima")
        @Min(value = 1, message = "A estadia mínima é de pelo menos 1 noite")
        Integer estadiaMinima,

        @NotNull(message = "Informe se o período está fechado para chegada")
        Boolean fechadoChegada,

        @NotNull(message = "Informe se o período está fechado para saída")
        Boolean fechadoSaida,

        @NotNull(message = "Informe se a venda está fechada")
        Boolean vendaFechada
) {

    @AssertTrue(message = "O fim deve ser igual ou depois do início")
    public boolean isFim() {
        return inicio == null || fim == null || !fim.isBefore(inicio);
    }
}
