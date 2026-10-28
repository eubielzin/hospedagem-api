package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Recebimento (RF-44): da conta de uma reserva ou de uma venda avulsa do PDV, nunca dos dois. Imutável. */
@Entity
@Getter
@Setter
@Table(name = "pagamento")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", updatable = false)
    private Reserva reserva;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", updatable = false)
    private Venda venda;

    @Column(name = "data", nullable = false, updatable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, updatable = false, length = 10)
    private FormaPagamento forma;

    @Column(name = "valor_bruto", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valorBruto;

    @Column(name = "taxa", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal taxa;

    @Column(name = "valor_liquido", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal valorLiquido;

    /** Só em dinheiro: o que o cliente entregou. */
    @Column(name = "valor_recebido", updatable = false, precision = 12, scale = 2)
    private BigDecimal valorRecebido;

    @Column(name = "descricao", nullable = false, updatable = false, length = 300)
    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
