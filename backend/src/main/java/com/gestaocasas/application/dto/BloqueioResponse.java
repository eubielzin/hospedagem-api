package com.gestaocasas.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/** fim é o último dia bloqueado (inclusive). usuario é o nome de quem bloqueou. */
public record BloqueioResponse(UUID id, UUID uhId, String uhNome, LocalDate inicio, LocalDate fim, String motivo,
                               String usuario, boolean ativo) {
}
