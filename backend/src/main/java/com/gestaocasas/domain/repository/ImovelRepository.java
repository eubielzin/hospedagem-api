package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Imovel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImovelRepository extends JpaRepository<Imovel, UUID>, JpaSpecificationExecutor<Imovel> {

    /** Bloqueia a linha do imóvel: serializa alterações concorrentes nas fotos (principal e ordem). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Imovel i where i.id = :id")
    Optional<Imovel> findByIdForUpdate(@Param("id") UUID id);
}
