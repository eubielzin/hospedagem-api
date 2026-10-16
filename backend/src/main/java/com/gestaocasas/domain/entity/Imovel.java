package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** O imóvel é a UH (unidade habitacional) das reservas, das tarifas e da governança. */
@Entity
@Getter
@Setter
@Table(name = "imovel")
public class Imovel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", columnDefinition = "text")
    private String descricao;

    @Column(name = "logradouro", nullable = false, length = 200)
    private String logradouro;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "complemento", length = 100)
    private String complemento;

    @Column(name = "bairro", length = 100)
    private String bairro;

    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Column(name = "cep", length = 9)
    private String cep;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoUh tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaUh categoria;

    @Column(name = "capacidade_adultos", nullable = false)
    private Integer capacidadeAdultos;

    @Column(name = "capacidade_criancas", nullable = false)
    private Integer capacidadeCriancas;

    @Column(name = "quartos", nullable = false)
    private Integer quartos;

    @Column(name = "banheiros", nullable = false)
    private Integer banheiros;

    @Column(name = "camas", nullable = false)
    private Integer camas;

    @Column(name = "valor_base_diaria", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBaseDiaria;

    /** Nulo: vale o horário padrão da configuração. */
    @Column(name = "horario_checkin")
    private LocalTime horarioCheckin;

    @Column(name = "horario_checkout")
    private LocalTime horarioCheckout;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusImovel status;

    /** Só LIVRE, SUJA ou MANUTENCAO; OCUPADA e BLOQUEADA são calculadas na leitura. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_governanca", nullable = false, length = 20)
    private StatusGovernanca statusGovernanca;

    @ElementCollection
    @CollectionTable(name = "imovel_comodidade", joinColumns = @JoinColumn(name = "imovel_id"))
    @Column(name = "comodidade", nullable = false, length = 80)
    private Set<String> comodidades = new LinkedHashSet<>();

    // Fotos com exclusão pendente no Storage não fazem mais parte do imóvel.
    @OneToMany(mappedBy = "imovel")
    @SQLRestriction("exclusao_pendente_em IS NULL")
    @OrderBy("ordem ASC, criadoEm ASC")
    private List<ImovelFoto> fotos = new ArrayList<>();

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    /** Calculada: não é gravada, para não divergir da soma. */
    public Integer getCapacidadeMaxima() {
        return capacidadeAdultos == null || capacidadeCriancas == null ? null : capacidadeAdultos + capacidadeCriancas;
    }

    public UUID getCategoriaId() {
        return categoria == null ? null : categoria.getId();
    }

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
