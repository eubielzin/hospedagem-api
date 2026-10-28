package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.RegimePensao;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * uhId é o id do imóvel. O valor vem da cotação pelas tarifas (RF-31): valorTotal é aceito só para o front
 * antigo continuar funcionando e é ignorado.
 */
public record ReservaRequest(
        @NotNull(message = "UH é obrigatória")
        UUID uhId,

        @NotNull(message = "Hóspede titular é obrigatório")
        UUID titularId,

        @NotNull(message = "Canal é obrigatório")
        UUID canalId,

        @NotNull(message = "Data de entrada é obrigatória")
        LocalDate checkin,

        @NotNull(message = "Data de saída é obrigatória")
        LocalDate checkout,

        @NotNull(message = "Informe o número de adultos")
        @Min(value = 1, message = "A reserva precisa de ao menos 1 adulto")
        Integer adultos,

        @Min(value = 0, message = "Crianças não pode ser negativo")
        Integer criancas,

        /** Sem regime informado: SEM_REFEICOES. */
        RegimePensao regimePensao,

        @Size(max = 60, message = "Código externo deve ter no máximo 60 caracteres")
        String codigoExterno,

        // Ignorado: o valor é o da cotação.
        BigDecimal valorTotal,

        @Size(max = 1000, message = "Observações devem ter no máximo 1000 caracteres")
        String observacoes
) {
}
