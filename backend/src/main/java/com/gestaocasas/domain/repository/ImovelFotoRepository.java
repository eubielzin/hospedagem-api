package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.ImovelFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImovelFotoRepository extends JpaRepository<ImovelFoto, UUID> {

    /** Inclui fotos com exclusão pendente, para que a exclusão possa ser repetida. */
    Optional<ImovelFoto> findByIdAndImovelId(UUID id, UUID imovelId);

    Optional<ImovelFoto> findByIdAndImovelIdAndExclusaoPendenteEmIsNull(UUID id, UUID imovelId);

    List<ImovelFoto> findByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(UUID imovelId);

    Optional<ImovelFoto> findFirstByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(UUID imovelId);

    boolean existsByImovelIdAndPrincipalTrue(UUID imovelId);

    long countByImovelId(UUID imovelId);

    @Query("select coalesce(max(f.ordem), -1) from ImovelFoto f where f.imovel.id = :imovelId")
    int findMaiorOrdem(@Param("imovelId") UUID imovelId);

    @Modifying(flushAutomatically = true)
    @Query("update ImovelFoto f set f.principal = false where f.imovel.id = :imovelId and f.principal = true")
    int desmarcarPrincipal(@Param("imovelId") UUID imovelId);
}
