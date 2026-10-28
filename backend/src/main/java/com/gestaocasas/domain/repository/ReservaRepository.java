package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Reserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface ReservaRepository extends JpaRepository<Reserva, UUID>, JpaSpecificationExecutor<Reserva> {

    /** Imóvel, titular e canal vêm na mesma consulta: a resposta mostra os nomes. */
    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findDetalhadaById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    List<Reserva> findByTitularIdOrderByCheckinDescCodigoDesc(UUID titularId);

    /**
     * Primeira reserva ativa (nem cancelada nem no-show) em uma das UHs que cruza o período
     * [checkin, checkout). Chame com as linhas das UHs bloqueadas (ver DisponibilidadeUh), para que duas
     * gravações simultâneas não passem as duas pela verificação.
     */
    @Query(value = """
            SELECT r.* FROM reserva r
            WHERE r.imovel_id IN (:imovelIds)
              AND r.status NOT IN ('CANCELADA', 'NO_SHOW')
              AND r.checkin < :checkout AND r.checkout > :checkin
              AND r.id <> :ignorarId
            ORDER BY r.checkin
            LIMIT 1
            """, nativeQuery = true)
    Optional<Reserva> findConflito(@Param("imovelIds") Collection<UUID> imovelIds, @Param("checkin") LocalDate checkin,
                                   @Param("checkout") LocalDate checkout, @Param("ignorarId") UUID ignorarId);

    /** Imóveis com hóspede na casa agora: a governança os mostra como OCUPADA. */
    @Query("select distinct r.imovel.id from Reserva r where r.status = com.gestaocasas.domain.enums.StatusReserva.HOSPEDADO")
    Set<UUID> findImoveisHospedados();

    /** Reservas que ainda vão ocupar ou ocupam o imóvel (reservadas ou hospedadas). */
    @Query("select count(r) > 0 from Reserva r where r.imovel.id = :imovelId and r.status in "
            + "(com.gestaocasas.domain.enums.StatusReserva.RESERVADO, com.gestaocasas.domain.enums.StatusReserva.HOSPEDADO)")
    boolean existeAtivaNoImovel(@Param("imovelId") UUID imovelId);

    @Query(value = "SELECT nextval('reserva_codigo_seq')", nativeQuery = true)
    long proximoNumero();
}
