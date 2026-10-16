package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.CategoriaUh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoriaUhRepository extends JpaRepository<CategoriaUh, UUID> {

    List<CategoriaUh> findAllByOrderByNivelAscNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
