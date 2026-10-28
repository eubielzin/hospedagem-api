package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** RF-04: a UH fica indisponível de inicio a fim (inclusive). Encerrado, nunca excluído (RN-15). */
@Entity
@Getter
@Setter
@Table(name = "bloqueio")
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, updatable = false)
    private Imovel imovel;

    @Column(name = "inicio", nullable = false)
    private LocalDate inicio;

    /** Último dia bloqueado (inclusive). */
    @Column(name = "fim", nullable = false)
    private LocalDate fim;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
