package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImovelRepository extends JpaRepository<Imovel, UUID>, JpaSpecificationExecutor<Imovel> {

    /** Bloqueia a linha do imóvel: serializa alterações concorrentes nas fotos (principal e ordem). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Imovel i where i.id = :id")
    Optional<Imovel> findByIdForUpdate(@Param("id") UUID id);

    /** UHs para a governança e o mapa: casas primeiro, depois o imóvel completo; cada grupo por nome. */
    @Query("select i from Imovel i order by i.tipo, lower(i.nome)")
    List<Imovel> findAllUhs();

    Optional<Imovel> findFirstByTipo(TipoUh tipo);

    List<Imovel> findByTipo(TipoUh tipo);

    @Query("select i.id from Imovel i")
    List<UUID> findAllIds();

    Optional<Imovel> findFirstByTipoOrderByCriadoEmAsc(TipoUh tipo);

    Optional<Imovel> findFirstByTipoAndStatusAndStatusGovernancaOrderByNome(TipoUh tipo, StatusImovel status,
                                                                          StatusGovernanca statusGovernanca);
}
