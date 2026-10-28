package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Venda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendaRepository extends JpaRepository<Venda, UUID>, JpaSpecificationExecutor<Venda> {

    /** Bloqueia a venda: cancelamento e devoluções simultâneos da mesma venda rodam um após o outro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> findByIdForUpdate(@Param("id") UUID id);

    /** Vendas avulsas concluídas em [desde, ate), com os itens: receita de consumo dos indicadores. */
    @Query("select distinct v from Venda v left join fetch v.itens i left join fetch i.produto "
            + "where v.status = com.gestaocasas.domain.enums.StatusVenda.CONCLUIDA "
            + "and v.tipo = com.gestaocasas.domain.enums.TipoVenda.AVULSA and v.criadoEm >= :desde and v.criadoEm < :ate")
    List<Venda> findConcluidasEntre(@Param("desde") Instant desde, @Param("ate") Instant ate);
}
