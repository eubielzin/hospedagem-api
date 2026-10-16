package com.gestaocasas.application.dto;

import java.util.UUID;

public record CategoriaUhResponse(
        UUID id,
        String nome,
        int nivel,
        String descricao,
        boolean ativa
) {
}
