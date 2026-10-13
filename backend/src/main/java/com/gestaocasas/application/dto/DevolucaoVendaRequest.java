package com.gestaocasas.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DevolucaoVendaRequest(
        @NotEmpty(message = "Informe ao menos um item")
        @Size(max = 100, message = "Uma devolução pode ter no máximo 100 itens")
        List<@NotNull(message = "Item não pode ser nulo") @Valid ItemVendaRequest> itens,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 300, message = "Motivo deve ter no máximo 300 caracteres")
        String motivo
) {
}
