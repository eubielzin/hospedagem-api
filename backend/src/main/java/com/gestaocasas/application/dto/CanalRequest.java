package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.TipoCanal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** comissao e cor são opcionais: na criação valem 0 e cinza; na edição, ausentes, ficam como estão. */
public record CanalRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String nome,

        @NotNull(message = "Tipo é obrigatório")
        TipoCanal tipo,

        @DecimalMin(value = "0", message = "A comissão vai de 0 a 100")
        @DecimalMax(value = "100", message = "A comissão vai de 0 a 100")
        @Digits(integer = 3, fraction = 2, message = "A comissão deve ter no máximo 2 casas decimais")
        BigDecimal comissao,

        @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "A cor deve estar no formato #rrggbb")
        String cor
) {

    public CanalRequest(String nome, TipoCanal tipo) {
        this(nome, tipo, null, null);
    }
}
