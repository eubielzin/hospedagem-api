package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-55. ocupacao de 0 a 1, só das casas ativas; com o imóvel completo reservado, todas ocupadas. Hóspedes na
 * casa: só reservas HOSPEDADO. As listas trazem as reservas sem o histórico.
 */
public record ResumoDiaResponse(
        LocalDate data,
        int totalUhs,
        int uhsOcupadas,
        BigDecimal ocupacao,
        int adultosNaCasa,
        int criancasNaCasa,
        BigDecimal diariasPrevistas,
        BigDecimal adr,
        BigDecimal revpar,
        List<ReservaResponse> chegadasHoje,
        List<ReservaResponse> saidasHoje,
        List<ReservaResponse> chegadasAmanha,
        List<ReservaResponse> saidasAmanha
) {
}
