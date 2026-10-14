package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório")
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 72, message = "Senha deve ter no máximo 72 caracteres")
        String senha
) {
}
