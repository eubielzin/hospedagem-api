package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Diária de uma categoria em um período, com as restrições de venda do período (RF-31 e RF-32). */
@Entity
@Getter
@Setter
@Table(name = "tarifa")
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaUh categoria;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    /** Última noite (inclusive) em que a tarifa vale. */
    @Column(name = "fim", nullable = false)
    private LocalDate fim;

    @Column(name = "valor_diaria", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorDiaria;

    @Column(name = "estadia_minima", nullable = false)
    private int estadiaMinima;

    @Column(name = "fechado_chegada", nullable = false)
    private boolean fechadoChegada;

    @Column(name = "fechado_saida", nullable = false)
    private boolean fechadoSaida;

    @Column(name = "venda_fechada", nullable = false)
    private boolean vendaFechada;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
