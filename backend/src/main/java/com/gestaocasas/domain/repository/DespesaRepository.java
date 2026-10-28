package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, UUID> {

    @Query("select d from Despesa d where d.vencimento between :inicio and :fim order by d.vencimento, d.criadoEm")
    List<Despesa> findPorVencimento(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Com data em [inicio, fim]: a do pagamento, ou o vencimento enquanto não paga. */
    @Query("select d from Despesa d where (d.dataPagamento is not null and d.dataPagamento between :inicio and :fim) "
            + "or (d.dataPagamento is null and d.vencimento between :inicio and :fim)")
    List<Despesa> findNoFluxo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
