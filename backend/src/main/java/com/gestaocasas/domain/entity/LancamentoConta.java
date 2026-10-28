package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Consumo na conta do quarto (RF-38). Nunca excluído: o estorno o marca e guarda o motivo (RN-14). */
@Entity
@Getter
@Setter
@Table(name = "lancamento_conta")
public class LancamentoConta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, updatable = false)
    private Reserva reserva;

    @Column(name = "data", nullable = false, updatable = false)
    private LocalDate data;

    @Column(name = "descricao", nullable = false, updatable = false, length = 150)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", updatable = false)
    private Produto produto;

    /** Se o lançamento baixou o estoque do imóvel (o produto controlava estoque naquele momento). */
    @Column(name = "baixou_estoque", nullable = false, updatable = false)
    private boolean baixouEstoque;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "valor_unitario", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "estornado", nullable = false)
    private boolean estornado;

    @Column(name = "motivo_estorno", length = 300)
    private String motivoEstorno;

    @Column(name = "estornado_em")
    private Instant estornadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estornado_por")
    private Usuario estornadoPor;

    public UUID getProdutoId() {
        return produto == null ? null : produto.getId();
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
