package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.RegimePensao;
import com.gestaocasas.domain.enums.StatusReserva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A UH da reserva é o imóvel. O período é [checkin, checkout): a noite do checkout não conta. */
@Entity
@Getter
@Setter
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo", nullable = false, length = 20, updatable = false)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false)
    private Imovel imovel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospede_id", nullable = false)
    private Hospede titular;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "canal_id", nullable = false)
    private Canal canal;

    @Column(name = "checkin", nullable = false)
    private LocalDate checkin;

    @Column(name = "checkout", nullable = false)
    private LocalDate checkout;

    @Column(name = "adultos", nullable = false)
    private int adultos;

    @Column(name = "criancas", nullable = false)
    private int criancas;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReserva status;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime_pensao", nullable = false, length = 20)
    private RegimePensao regimePensao;

    /** Código da reserva no canal de origem (RF-13). */
    @Column(name = "codigo_externo", length = 60)
    private String codigoExterno;

    @Column(name = "observacoes", length = 1000)
    private String observacoes;

    /** Nula enquanto não preenchida: todas as colunas vazias. */
    @Embedded
    private Fnrh fnrh;

    /** Só em reservas canceladas ou no-show. */
    @Embedded
    private Encerramento encerramento;

    /** RN-05: o check-out foi feito com saldo em aberto, autorizado. */
    @Column(name = "pendencia_autorizada", nullable = false)
    private boolean pendenciaAutorizada;

    @Column(name = "checkin_realizado_em")
    private Instant checkinRealizadoEm;

    @Column(name = "checkout_realizado_em")
    private Instant checkoutRealizadoEm;

    /** Momento do cancelamento ou do no-show. */
    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    // MERGE também: o saveAndFlush de uma reserva já gravada faz merge, e sem a cascata o evento novo sairia vazio.
    @OneToMany(mappedBy = "reserva", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("dataHora ASC")
    private List<ReservaEvento> historico = new ArrayList<>();

    /** Noites da estadia: o período é [checkin, checkout). */
    public int getNoites() {
        return (int) ChronoUnit.DAYS.between(checkin, checkout);
    }

    /** Acrescenta uma linha ao histórico; gravada junto com a reserva. */
    public void registrar(String descricao, Instant quando, Usuario usuario) {
        ReservaEvento evento = new ReservaEvento();
        evento.setReserva(this);
        evento.setDataHora(quando);
        evento.setDescricao(descricao.length() > 500 ? descricao.substring(0, 500) : descricao);
        evento.setUsuario(usuario);
        historico.add(evento);
    }

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
