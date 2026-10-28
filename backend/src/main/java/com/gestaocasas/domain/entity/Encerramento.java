package com.gestaocasas.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cancelamento ou no-show. Os valores são digitados (a política de multa ainda está a validar). RN-08: o
 * valor da reserva encerrada nunca é receita; só o valor retido.
 */
@Embeddable
@Getter
@Setter
public class Encerramento {

    @Column(name = "encerramento_data")
    private LocalDate data;

    @Column(name = "encerramento_motivo", length = 500)
    private String motivo;

    @Column(name = "encerramento_multa", precision = 12, scale = 2)
    private BigDecimal multa;

    @Column(name = "encerramento_valor_retido", precision = 12, scale = 2)
    private BigDecimal valorRetido;

    @Column(name = "encerramento_valor_devolver", precision = 12, scale = 2)
    private BigDecimal valorDevolver;
}
