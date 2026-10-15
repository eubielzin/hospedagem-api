package com.gestaocasas.application.dto;

import com.gestaocasas.application.validation.DocumentoValido;
import com.gestaocasas.domain.enums.TipoDocumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@DocumentoValido
public record HospedeRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        @NotNull(message = "Tipo de documento é obrigatório")
        TipoDocumento tipoDocumento,

        // Opcional no cadastro: só é exigido no check-in (RN-04).
        @Size(max = 30, message = "Documento deve ter no máximo 30 caracteres")
        String numeroDocumento,

        @NotBlank(message = "Informe a nacionalidade")
        @Size(max = 60, message = "Nacionalidade deve ter no máximo 60 caracteres")
        String nacionalidade,

        @Size(max = 30, message = "Telefone deve ter no máximo 30 caracteres")
        String telefone
) {
}
