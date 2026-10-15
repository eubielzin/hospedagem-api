package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Canal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CanalRepository extends JpaRepository<Canal, UUID> {

    List<Canal> findByAtivoTrueOrderByNome();

    List<Canal> findAllByOrderByNome();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
