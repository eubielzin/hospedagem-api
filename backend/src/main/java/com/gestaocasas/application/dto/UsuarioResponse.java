package com.gestaocasas.application.dto;

import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String nome,
        String email,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
