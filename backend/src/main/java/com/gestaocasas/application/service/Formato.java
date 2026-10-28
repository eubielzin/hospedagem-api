package com.gestaocasas.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Datas e valores nas mensagens para o usuário: dd/MM/aaaa e R$ 1.234,56. */
public final class Formato {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale BRASIL = Locale.of("pt", "BR");

    private Formato() {
    }

    public static String data(LocalDate data) {
        return data.format(DATA);
    }

    public static String dinheiro(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(BRASIL));
        return "R$ " + formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }
}
