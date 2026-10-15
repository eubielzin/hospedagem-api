package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoCanal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CanalRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String nome,

        @NotNull(message = "Tipo é obrigatório")
        TipoCanal tipo
) {
}
