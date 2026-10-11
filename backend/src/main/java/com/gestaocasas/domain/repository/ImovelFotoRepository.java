package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.ImovelFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImovelFotoRepository extends JpaRepository<ImovelFoto, UUID> {

    Optional<ImovelFoto> findByIdAndImovelId(UUID id, UUID imovelId);

    long countByImovelId(UUID imovelId);
}
