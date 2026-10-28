package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ocupação e receita já vendidas (on the books). vendidas7/30: reservas feitas nos últimos 7 e 30 dias. */
public record PrevisaoResponse(LocalDate inicio, List<Dia> dias, List<Semana> semanas, int noitesVendidas,
                               BigDecimal receita, double ocupacao30, double ocupacao60, double ocupacao90,
                               int vendidas7, int vendidas30) {

    public record Dia(LocalDate data, double ocupacao, BigDecimal receita, int chegadas) {
    }

    public record Semana(LocalDate inicio, double ocupacao, BigDecimal receita) {
    }
}
