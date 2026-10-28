package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Configurações da pousada (RF-67): uma única linha, de id 1. */
@Entity
@Getter
@Setter
@Table(name = "configuracao")
public class Configuracao {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "nome_pousada", nullable = false, length = 120)
    private String nomePousada;

    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    /** Padrão dos imóveis sem horário próprio. */
    @Column(name = "horario_checkin", nullable = false)
    private LocalTime horarioCheckin;

    @Column(name = "horario_checkout", nullable = false)
    private LocalTime horarioCheckout;

    /** RN-12: até esta idade o hóspede conta como criança (a validar). */
    @Column(name = "idade_limite_crianca", nullable = false)
    private int idadeLimiteCrianca;

    @Column(name = "politica_cancelamento", nullable = false, length = 2000)
    private String politicaCancelamento;

    // Lista separada por vírgulas; use getFormasPagamento/setFormasPagamento.
    @Column(name = "formas_pagamento", nullable = false, length = 60)
    private String formasPagamentoTexto;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public List<FormaPagamento> getFormasPagamento() {
        return Arrays.stream(formasPagamentoTexto.split(",")).map(FormaPagamento::valueOf).toList();
    }

    public void setFormasPagamento(List<FormaPagamento> formas) {
        formasPagamentoTexto = formas.stream().distinct().map(Enum::name).collect(Collectors.joining(","));
    }

    @PrePersist
    @PreUpdate
    void aoGravar() {
        atualizadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
