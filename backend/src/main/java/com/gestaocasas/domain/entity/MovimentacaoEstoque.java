package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Registro imutável: depois de gravada, a movimentação nunca é alterada nem apagada. */
@Entity
@Immutable
@Getter
@Setter
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, updatable = false)
    private Imovel imovel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoMovimentacao tipo;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "saldo_anterior", nullable = false, updatable = false)
    private int saldoAnterior;

    @Column(name = "saldo_posterior", nullable = false, updatable = false)
    private int saldoPosterior;

    @Column(name = "motivo", updatable = false, length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "referencia_tipo", updatable = false, length = 30)
    private TipoReferenciaMovimentacao referenciaTipo;

    @Column(name = "referencia_id", updatable = false)
    private UUID referenciaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
