package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Reserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
     * Primeira reserva que ainda ocupa o imóvel (reservada ou hospedada) e cruza o período
     * [checkin, checkout). Chame com a linha do imóvel bloqueada, para que duas criações
     * simultâneas não passem as duas pela verificação.
     */
    @Query(value = """
            SELECT r.* FROM reserva r
            WHERE r.imovel_id = :imovelId
              AND r.status IN ('RESERVADO', 'HOSPEDADO')
              AND r.checkin < :checkout AND r.checkout > :checkin
              AND r.id <> :ignorarId
            ORDER BY r.checkin
            LIMIT 1
            """, nativeQuery = true)
    Optional<Reserva> findConflito(@Param("imovelId") UUID imovelId, @Param("checkin") LocalDate checkin,
                                   @Param("checkout") LocalDate checkout, @Param("ignorarId") UUID ignorarId);

    @Query(value = "SELECT nextval('reserva_codigo_seq')", nativeQuery = true)
    long proximoNumero();
}
