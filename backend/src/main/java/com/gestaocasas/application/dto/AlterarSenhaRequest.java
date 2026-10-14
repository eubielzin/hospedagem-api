package com.gestaocasas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        @Size(max = 72, message = "Senha atual deve ter no máximo 72 caracteres")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres")
        String novaSenha
) {
}
