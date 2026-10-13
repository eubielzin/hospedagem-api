package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.OperacaoIdempotente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "chave_idempotencia")
public class ChaveIdempotencia {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, updatable = false, length = 40)
    private OperacaoIdempotente operacao;

    @Column(name = "chave", nullable = false, updatable = false, length = 100)
    private String chave;

    @Column(name = "impressao_digital", nullable = false, updatable = false, length = 64)
    private String impressaoDigital;

    /** Resultado da operação (a venda, a movimentação), devolvido quando a requisição se repete. */
    @Column(name = "recurso_id")
    private UUID recursoId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
}
