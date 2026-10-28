package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.LancamentoConta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface LancamentoContaRepository extends JpaRepository<LancamentoConta, UUID> {

    @Query("select l from LancamentoConta l where l.reserva.id = :reservaId order by l.criadoEm, l.id")
    List<LancamentoConta> findDaReserva(@Param("reservaId") UUID reservaId);

    @EntityGraph(attributePaths = "produto")
    @Query("select l from LancamentoConta l where not l.estornado and l.data between :inicio and :fim")
    List<LancamentoConta> findNaoEstornadosEntre(@Param("inicio") LocalDate inicio,
                                                 @Param("fim") LocalDate fim);

    @Query("select l from LancamentoConta l where l.reserva.id in :reservaIds")
    List<LancamentoConta> findDasReservas(@Param("reservaIds") Collection<UUID> reservaIds);
}
