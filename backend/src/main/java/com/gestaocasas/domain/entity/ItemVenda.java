package com.gestaocasas.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Nome, código e preço são a fotografia do produto no momento da venda e nunca mudam depois. */
@Entity
@Getter
@Setter
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false, updatable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @Column(name = "produto_codigo", nullable = false, updatable = false, length = 40)
    private String produtoCodigo;

    @Column(name = "produto_nome", nullable = false, updatable = false, length = 150)
    private String produtoNome;

    @Column(name = "baixou_estoque", nullable = false, updatable = false)
    private boolean baixouEstoque;

    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    @Column(name = "quantidade_devolvida", nullable = false)
    private int quantidadeDevolvida;

    @Column(name = "preco_unitario", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /** Unidades que ainda estão com o cliente: vendidas e não devolvidas. */
    public int quantidadeNaoDevolvida() {
        return quantidade - quantidadeDevolvida;
    }
}
