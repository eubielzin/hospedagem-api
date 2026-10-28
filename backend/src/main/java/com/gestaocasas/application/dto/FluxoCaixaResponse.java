package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** RF-50. saldoPrevisto = saldoRealizado + entradasPrevistas − saidasPrevistas. */
public record FluxoCaixaResponse(LocalDate inicio, LocalDate fim, BigDecimal entradasRealizadas,
                                 BigDecimal saidasRealizadas, BigDecimal entradasPrevistas, BigDecimal saidasPrevistas,
                                 BigDecimal saldoRealizado, BigDecimal saldoPrevisto, List<Linha> linhas) {

    public enum Tipo { ENTRADA, SAIDA }

    public record Linha(LocalDate data, String descricao, Tipo tipo, BigDecimal valor, boolean realizado) {
    }
}
