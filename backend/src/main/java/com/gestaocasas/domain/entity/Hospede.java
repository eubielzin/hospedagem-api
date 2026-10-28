package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** As colunas geradas documento_busca e nome_busca existem só no banco, para busca e unicidade. */
@Entity
@Getter
@Setter
@Table(name = "hospede")
public class Hospede {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private TipoDocumento tipoDocumento;

    // Vazio quando ainda não informado: o documento só é exigido no check-in.
    @Column(name = "numero_documento", nullable = false, length = 30)
    private String numeroDocumento;

    @Column(name = "nacionalidade", nullable = false, length = 60)
    private String nacionalidade;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "email", length = 254)
    private String email;

    /** "Cidade/UF" ou "Cidade/País": origem do hóspede nos indicadores quando não há FNRH. */
    @Column(name = "endereco", length = 150)
    private String endereco;

    /** Aparece na lista de pessoas do dia (RF-53). */
    @Column(name = "observacoes_alimentares", length = 500)
    private String observacoesAlimentares;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

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
