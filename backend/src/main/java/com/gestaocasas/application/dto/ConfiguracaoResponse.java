package com.gestaocasas.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gestaocasas.domain.enums.FormaPagamento;

import java.time.LocalTime;
import java.util.List;

/** Horários em "HH:mm". */
public record ConfiguracaoResponse(
        String nomePousada,
        String cidade,
        @JsonFormat(pattern = "HH:mm") LocalTime horarioCheckin,
        @JsonFormat(pattern = "HH:mm") LocalTime horarioCheckout,
        int idadeLimiteCrianca,
        String politicaCancelamento,
        List<FormaPagamento> formasPagamento
) {
}
