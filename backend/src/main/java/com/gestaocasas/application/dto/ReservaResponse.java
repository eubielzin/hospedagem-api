package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusReserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Com os nomes já resolvidos, como a listagem da tela precisa. uhId/uhNome são do imóvel. */
public record ReservaResponse(
        UUID id,
        String codigo,
        UUID uhId,
        String uhNome,
        UUID titularId,
        String titularNome,
        UUID canalId,
        String canalNome,
        LocalDate checkin,
        LocalDate checkout,
        int adultos,
        int criancas,
        BigDecimal valorTotal,
        StatusReserva status,
        String observacoes,
        String motivoCancelamento,
        Instant criadaEm,
        Instant checkinRealizadoEm,
        Instant checkoutRealizadoEm,
        Instant canceladaEm,
        Instant atualizadoEm
) {
}
