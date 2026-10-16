package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TarifaResponse(
        UUID id,
        UUID categoriaId,
        String nome,
        LocalDate inicio,
        LocalDate fim,
        BigDecimal valorDiaria,
        int estadiaMinima,
        boolean fechadoChegada,
        boolean fechadoSaida,
        boolean vendaFechada
) {
}
