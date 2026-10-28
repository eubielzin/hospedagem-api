package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoVenda;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false, updatable = false)
    private Imovel imovel;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 20)
    private TipoVenda tipo;

    // Sem relacionamento: o PDV só registra vendas avulsas; o consumo do quarto vai para a conta da reserva.
    @Column(name = "reserva_id", updatable = false)
    private UUID reservaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusVenda status;

    @Enumerated(EnumType.STRING)
    @Column(name = "pagamento_status", nullable = false, length = 20)
    private StatusPagamento pagamentoStatus;

    @Column(name = "total", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "observacao", updatable = false, length = 300)
    private String observacao;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    /** Nulo enquanto a venda não foi paga. */
    @OneToOne(mappedBy = "venda", fetch = FetchType.LAZY)
    private Pagamento pagamento;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL)
    @OrderBy("produtoNome ASC, id ASC")
    private List<ItemVenda> itens = new ArrayList<>();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    public void adicionarItem(ItemVenda item) {
        item.setVenda(this);
        itens.add(item);
    }

    public void cancelar(String motivo) {
        status = StatusVenda.CANCELADA;
        motivoCancelamento = motivo;
        canceladoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
