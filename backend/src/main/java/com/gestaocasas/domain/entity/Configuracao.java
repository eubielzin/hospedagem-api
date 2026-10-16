package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

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

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    @PreUpdate
    void aoGravar() {
        atualizadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
