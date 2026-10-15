package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoDocumento;

import java.time.Instant;
import java.util.UUID;

/** numeroDocumento nunca é nulo: vem vazio enquanto o documento não foi informado. */
public record HospedeResponse(
        UUID id,
        String nome,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nacionalidade,
        String telefone,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
