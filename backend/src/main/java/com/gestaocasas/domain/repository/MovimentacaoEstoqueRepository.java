package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, UUID>,
        JpaSpecificationExecutor<MovimentacaoEstoque> {

    // A listagem mostra código e nome do produto: carrega junto, em vez de uma consulta por linha.
    @Override
    @EntityGraph(attributePaths = "produto")
    Page<MovimentacaoEstoque> findAll(Specification<MovimentacaoEstoque> spec, Pageable pageable);

    /** Movimentações geradas por uma mesma operação (as duas pontas de uma transferência, por exemplo). */
    @EntityGraph(attributePaths = "produto")
    List<MovimentacaoEstoque> findByReferenciaIdOrderByCriadoEmAscIdAsc(UUID referenciaId);
}
