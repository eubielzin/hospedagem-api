package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusGovernanca;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record GovernancaRequest(
        @NotNull(message = "Informe o status")
        StatusGovernanca status
) {

    /** OCUPADA e BLOQUEADA vêm das reservas e dos bloqueios, não da governança. */
    @AssertTrue(message = "O status deve ser LIVRE, SUJA ou MANUTENCAO")
    public boolean isStatus() {
        return status == null || status.manual();
    }
}
