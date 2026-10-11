package com.gestaocasas.domain.entity;

import com.gestaocasas.domain.enums.StorageProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "imovel_foto")
public class ImovelFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imovel_id", nullable = false)
    private Imovel imovel;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "descricao", length = 200)
    private String descricao;

    @Column(name = "ordem", nullable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider = StorageProvider.EXTERNA;

    // Caminho do objeto no bucket ({imovelId}/{imagemId}.{extensao}); nulo para fotos cadastradas por URL.
    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "principal", nullable = false)
    private boolean principal;

    // Exclusão pedida, arquivo ainda não removido do Storage. A foto deixa de aparecer nas consultas.
    @Column(name = "exclusao_pendente_em")
    private Instant exclusaoPendenteEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
