package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, UUID> {

    @Query("select t from Tarifa t order by t.inicio, t.fim, t.nome")
    List<Tarifa> findAllOrdenadas();
}
