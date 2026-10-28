package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.MeioTransporte;
import com.gestaocasas.domain.enums.MotivoViagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FnrhRequest(
        @NotBlank(message = "Informe a cidade de procedência")
        @Size(max = 120, message = "Cidade de procedência deve ter no máximo 120 caracteres")
        String cidadeProcedencia,

        @NotBlank(message = "Informe a cidade de destino")
        @Size(max = 120, message = "Cidade de destino deve ter no máximo 120 caracteres")
        String cidadeDestino,

        @NotNull(message = "Informe o motivo da viagem")
        MotivoViagem motivoViagem,

        @NotNull(message = "Informe o meio de transporte")
        MeioTransporte meioTransporte
) {
}
