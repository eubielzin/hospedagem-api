package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Pagamento;
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
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    @EntityGraph(attributePaths = "usuario")
    @Query("select p from Pagamento p where p.reserva.id = :reservaId order by p.criadoEm, p.id")
    List<Pagamento> findDaReserva(@Param("reservaId") UUID reservaId);

    @Query("select p from Pagamento p where p.reserva.id in :reservaIds")
    List<Pagamento> findDasReservas(@Param("reservaIds") Collection<UUID> reservaIds);

    /** De reservas e de vendas, com data em [inicio, fim]; o mais recente primeiro. */
    @EntityGraph(attributePaths = {"usuario", "reserva", "venda"})
    @Query("select p from Pagamento p where p.data between :inicio and :fim order by p.data desc, p.criadoEm desc")
    List<Pagamento> findNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
