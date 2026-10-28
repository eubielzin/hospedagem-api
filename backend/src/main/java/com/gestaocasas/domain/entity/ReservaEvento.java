package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Linha do histórico da reserva (RN-14). Imutável: o banco recusa alteração e exclusão. */
@Entity
@Getter
@Setter
@Table(name = "reserva_evento")
public class ReservaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, updatable = false)
    private Reserva reserva;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(name = "descricao", nullable = false, updatable = false, length = 500)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;
}
