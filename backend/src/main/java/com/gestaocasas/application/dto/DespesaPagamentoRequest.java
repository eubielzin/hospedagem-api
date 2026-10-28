package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DespesaPagamentoRequest(
        @NotNull(message = "Informe a data do pagamento")
        LocalDate data,

        @Size(max = 300, message = "Comprovante deve ter no máximo 300 caracteres")
        String comprovante
) {
}
