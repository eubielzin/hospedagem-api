package com.gestaocasas.application.dto;

import java.time.Instant;

/** Envie o token em todas as rotas protegidas: {@code Authorization: Bearer <token>}. */
public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEm,
        UsuarioResponse usuario
) {
}
