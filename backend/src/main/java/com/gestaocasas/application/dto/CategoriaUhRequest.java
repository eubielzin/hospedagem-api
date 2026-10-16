package com.gestaocasas.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaUhRequest(
        @NotBlank(message = "Informe o nome da categoria")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String nome,

        @NotNull(message = "Informe o nível")
        @Min(value = 1, message = "O nível deve ser 1 ou mais")
        Integer nivel,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String descricao
) {
}
