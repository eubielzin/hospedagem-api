package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstornoRequest(
        @NotBlank(message = "Informe o motivo do estorno")
        @Size(max = 300, message = "Motivo deve ter no máximo 300 caracteres")
        String motivo
) {
}
