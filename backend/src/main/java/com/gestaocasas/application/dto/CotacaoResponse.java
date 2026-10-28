package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Valor da estadia noite a noite; tarifa é o nome da tarifa que valeu na noite. */
public record CotacaoResponse(int noites, BigDecimal valorTotal, List<Diaria> diarias) {

    public record Diaria(LocalDate data, BigDecimal valor, String tarifa) {
    }
}
