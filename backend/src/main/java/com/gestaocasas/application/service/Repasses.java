package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.RepasseResponse;
import com.gestaocasas.domain.entity.Reserva;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** RN-09: o repasse da OTA são as diárias menos a comissão do canal (a da época da reserva). */
public final class Repasses {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private Repasses() {
    }

    public static BigDecimal comissao(BigDecimal valor, BigDecimal percentual) {
        return valor.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
    }

    /** A previsão é o dia do check-out: premissa do protótipo, a validar (cada OTA paga num prazo). */
    public static RepasseResponse de(Reserva reserva) {
        BigDecimal comissao = comissao(reserva.getValorTotal(), reserva.getComissaoCanal());
        return new RepasseResponse(reserva.getId(), reserva.getCodigo(), reserva.getCanal().getNome(),
                reserva.getValorTotal(), comissao, reserva.getValorTotal().subtract(comissao), reserva.getCheckout(),
                reserva.getRepasseRecebidoEm());
    }
}
