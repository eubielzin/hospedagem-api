package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Size;

public record CancelamentoReservaRequest(
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        String motivo
) {
}
