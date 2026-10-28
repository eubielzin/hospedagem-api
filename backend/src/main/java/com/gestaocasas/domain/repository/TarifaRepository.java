package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, UUID> {

    @Query("select t from Tarifa t order by t.inicio, t.fim, t.nome")
    List<Tarifa> findAllOrdenadas();

    /** Tarifas da categoria que valem em algum dia de [inicio, fim]. */
    @Query("select t from Tarifa t where t.categoria.id = :categoriaId and t.inicio <= :fim and t.fim >= :inicio")
    List<Tarifa> findDaCategoriaNoPeriodo(@Param("categoriaId") UUID categoriaId, @Param("inicio") LocalDate inicio,
                                          @Param("fim") LocalDate fim);
}
