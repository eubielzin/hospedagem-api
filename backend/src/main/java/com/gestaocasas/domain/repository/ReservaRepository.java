package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Reserva;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /** Ativas (nem canceladas nem no-show) que ocupam alguma noite ou têm entrada ou saída em [desde, ate]. */
    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    @Query("select r from Reserva r where r.status not in (com.gestaocasas.domain.enums.StatusReserva.CANCELADA, "
            + "com.gestaocasas.domain.enums.StatusReserva.NO_SHOW) and r.checkin <= :ate and r.checkout >= :desde")
    List<Reserva> findAtivasEntre(@Param("desde") LocalDate desde, @Param("ate") LocalDate ate);

    /** Hospedadas que dormem na casa na noite do dia: checkin <= dia < checkout. */
    @EntityGraph(attributePaths = {"imovel", "titular"})
    @Query("select r from Reserva r where r.status = com.gestaocasas.domain.enums.StatusReserva.HOSPEDADO "
            + "and r.checkin <= :dia and r.checkout > :dia")
    List<Reserva> findHospedadasNaNoite(@Param("dia") LocalDate dia);

    /** RN-09: reservas ativas de canal OTA, cada uma com um repasse a receber. */
    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    @Query("select r from Reserva r where r.canal.tipo = com.gestaocasas.domain.enums.TipoCanal.OTA and r.status not in "
            + "(com.gestaocasas.domain.enums.StatusReserva.CANCELADA, com.gestaocasas.domain.enums.StatusReserva.NO_SHOW) "
            + "order by r.checkout, r.codigo")
    List<Reserva> findComRepasse();

    /** Repasses com data em [inicio, fim]: o recebimento, ou o checkout (previsão) enquanto não recebido. */
    @EntityGraph(attributePaths = {"canal"})
    @Query("select r from Reserva r where r.canal.tipo = com.gestaocasas.domain.enums.TipoCanal.OTA and r.status not in "
            + "(com.gestaocasas.domain.enums.StatusReserva.CANCELADA, com.gestaocasas.domain.enums.StatusReserva.NO_SHOW) "
            + "and ((r.repasseRecebidoEm is not null and r.repasseRecebidoEm between :inicio and :fim) "
            + "or (r.repasseRecebidoEm is null and r.checkout between :inicio and :fim))")
    List<Reserva> findRepassesNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** RN-08: encerramentos com valor retido (multa) em [inicio, fim], que entram como receita. */
    @Query("select r from Reserva r where r.encerramento.valorRetido > 0 and r.encerramento.data between :inicio and :fim")
    List<Reserva> findComValorRetidoNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * RF-47: hospedadas ou com check-out feito cuja conta tem saldo. saldo = diárias − repasse (OTA) + consumos não
     * estornados − pagamentos; a mesma conta de ContaReserva, feita no banco.
     */
    @Query(value = """
            SELECT r.id AS "reservaId", s.saldo AS "saldo" FROM reserva r
            JOIN canal c ON c.id = r.canal_id
            CROSS JOIN LATERAL (SELECT r.valor_total
                    - CASE WHEN c.tipo = 'OTA' THEN r.valor_total ELSE 0 END
                    + coalesce((SELECT sum(l.total) FROM lancamento_conta l
                                WHERE l.reserva_id = r.id AND NOT l.estornado), 0)
                    - coalesce((SELECT sum(p.valor_bruto) FROM pagamento p WHERE p.reserva_id = r.id), 0) AS saldo) s
            WHERE r.status IN ('HOSPEDADO', 'CHECKOUT_REALIZADO') AND s.saldo > 0
            """, nativeQuery = true)
    List<SaldoReserva> findSaldosEmAberto();

    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    @Query("select r from Reserva r where r.id in :ids")
    List<Reserva> findDetalhadasByIdIn(@Param("ids") Collection<UUID> ids);

    /** Para os indicadores: qualquer status, ocupando alguma noite de [desde, ate] ou encerradas nele. */
    @EntityGraph(attributePaths = {"imovel", "titular", "canal"})
    @Query("select r from Reserva r where (r.checkin <= :ate and r.checkout > :desde) "
            + "or r.encerramento.data between :desde and :ate")
    List<Reserva> findParaIndicadores(@Param("desde") LocalDate desde, @Param("ate") LocalDate ate);

    /** Por titular, o checkin da primeira reserva ativa: base da recorrência. */
    @Query(value = "SELECT hospede_id, min(checkin) FROM reserva WHERE status NOT IN ('CANCELADA', 'NO_SHOW') "
            + "GROUP BY hospede_id", nativeQuery = true)
    List<Object[]> findPrimeiraEstadiaPorTitular();

    /** Ativas feitas a partir do instante (ritmo de vendas). */
    @Query("select count(r) from Reserva r where r.criadoEm >= :desde and r.status not in "
            + "(com.gestaocasas.domain.enums.StatusReserva.CANCELADA, com.gestaocasas.domain.enums.StatusReserva.NO_SHOW)")
    long countAtivasCriadasDesde(@Param("desde") Instant desde);

    @Query(value = "SELECT nextval('reserva_codigo_seq')", nativeQuery = true)
    long proximoNumero();
}
