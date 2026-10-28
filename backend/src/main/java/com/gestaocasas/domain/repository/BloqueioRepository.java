package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Bloqueio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface BloqueioRepository extends JpaRepository<Bloqueio, UUID> {

    /**
     * Primeiro bloqueio ativo em uma das UHs que cruza o período [inicio, fimExclusivo). O fim do bloqueio é
     * inclusive: ele ocupa até fim + 1 dia.
     */
    @Query(value = """
            SELECT b.* FROM bloqueio b
            WHERE b.imovel_id IN (:imovelIds)
              AND b.ativo
              AND b.inicio < :fimExclusivo AND b.fim >= :inicio
            ORDER BY b.inicio
            LIMIT 1
            """, nativeQuery = true)
    Optional<Bloqueio> findConflito(@Param("imovelIds") Collection<UUID> imovelIds, @Param("inicio") LocalDate inicio,
                                    @Param("fimExclusivo") LocalDate fimExclusivo);

    /** Ativos que cruzam [inicio, fim] (ambos inclusive), para o mapa. */
    @EntityGraph(attributePaths = {"imovel", "usuario"})
    @Query("select b from Bloqueio b where b.ativo and b.inicio <= :fim and b.fim >= :inicio order by b.inicio")
    List<Bloqueio> findAtivosNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @EntityGraph(attributePaths = {"imovel", "usuario"})
    @Query("select b from Bloqueio b where b.id = :id")
    Optional<Bloqueio> findDetalhadoById(@Param("id") UUID id);

    /** UHs com bloqueio vigente no dia: a governança as mostra como BLOQUEADA. */
    @Query("select distinct b.imovel.id from Bloqueio b where b.ativo and b.inicio <= :dia and b.fim >= :dia")
    Set<UUID> findImoveisBloqueadosEm(@Param("dia") LocalDate dia);
}
