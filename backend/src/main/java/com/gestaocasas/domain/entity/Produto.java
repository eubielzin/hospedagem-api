package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.CategoriaProduto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo", nullable = false, length = 40)
    private String codigo;

    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaProduto categoria;

    @Column(name = "unidade", nullable = false, length = 10)
    private String unidade;

    @Column(name = "preco_custo", precision = 12, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "controla_estoque", nullable = false)
    private boolean controlaEstoque;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    // PostgreSQL guarda microssegundos: truncar mantém a resposta da escrita igual à da leitura.
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
