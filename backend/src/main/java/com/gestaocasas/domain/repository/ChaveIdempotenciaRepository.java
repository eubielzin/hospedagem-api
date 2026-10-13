package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.ChaveIdempotencia;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, UUID> {

    /**
     * Tenta ocupar a chave. Se outra transação estiver usando a mesma chave, espera ela terminar:
     * devolve 1 se a chave ficou com esta transação e 0 se já pertencia a uma operação concluída.
     */
    @Modifying
    @Query(value = """
            INSERT INTO chave_idempotencia (id, operacao, chave, impressao_digital, criado_em)
            VALUES (gen_random_uuid(), :operacao, :chave, :impressaoDigital, now())
            ON CONFLICT (operacao, chave) DO NOTHING
            """, nativeQuery = true)
    int ocupar(@Param("operacao") String operacao, @Param("chave") String chave,
               @Param("impressaoDigital") String impressaoDigital);

    Optional<ChaveIdempotencia> findByOperacaoAndChave(OperacaoIdempotente operacao, String chave);
}
