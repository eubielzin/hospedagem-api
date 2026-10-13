package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.EstoqueImovel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstoqueImovelRepository extends JpaRepository<EstoqueImovel, UUID> {

    /**
     * Todos os produtos que controlam estoque, cada um com o saldo que tem neste imóvel.
     * O join é restrito ao imóvel informado: saldos de outras casas nunca entram no resultado.
     *
     * @param padraoNome padrão de LIKE em minúsculas; "%" para não filtrar
     */
    @Query(value = """
            select p as produto, e as estoque
            from Produto p
            left join EstoqueImovel e on e.produto = p and e.imovel.id = :imovelId
            where p.controlaEstoque = true
              and lower(p.nome) like :padraoNome escape '\\'
              and (:qualquerSituacao = true or p.ativo = :ativo)
            order by p.nome, p.id
            """, countQuery = """
            select count(p) from Produto p
            where p.controlaEstoque = true
              and lower(p.nome) like :padraoNome escape '\\'
              and (:qualquerSituacao = true or p.ativo = :ativo)
            """)
    Page<ProdutoComEstoque> listarPorImovel(@Param("imovelId") UUID imovelId,
                                            @Param("padraoNome") String padraoNome,
                                            @Param("qualquerSituacao") boolean qualquerSituacao,
                                            @Param("ativo") boolean ativo,
                                            Pageable pageable);

    /** Produtos ativos deste imóvel com mínimo definido e saldo igual ou inferior a ele. */
    @Query(value = """
            select e from EstoqueImovel e join fetch e.produto p
            where e.imovel.id = :imovelId
              and p.ativo = true and p.controlaEstoque = true
              and e.estoqueMinimo > 0 and e.quantidadeAtual <= e.estoqueMinimo
            order by p.nome, p.id
            """, countQuery = """
            select count(e) from EstoqueImovel e join e.produto p
            where e.imovel.id = :imovelId
              and p.ativo = true and p.controlaEstoque = true
              and e.estoqueMinimo > 0 and e.quantidadeAtual <= e.estoqueMinimo
            """)
    Page<EstoqueImovel> listarAlertas(@Param("imovelId") UUID imovelId, Pageable pageable);

    /**
     * Garante que a linha de saldo exista, começando em zero. Com duas requisições simultâneas,
     * a segunda simplesmente não insere nada, em vez de falhar na constraint única.
     */
    @Modifying
    @Query(value = """
            INSERT INTO estoque_imovel (id, imovel_id, produto_id, quantidade_atual, estoque_minimo,
                                        criado_em, atualizado_em)
            VALUES (gen_random_uuid(), :imovelId, :produtoId, 0, 0, now(), now())
            ON CONFLICT (imovel_id, produto_id) DO NOTHING
            """, nativeQuery = true)
    void criarSeAusente(@Param("imovelId") UUID imovelId, @Param("produtoId") UUID produtoId);

    /** Bloqueia a linha de saldo: serializa alterações concorrentes do mesmo produto na mesma casa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstoqueImovel e join fetch e.produto where e.imovel.id = :imovelId and e.produto.id = :produtoId")
    Optional<EstoqueImovel> findForUpdate(@Param("imovelId") UUID imovelId, @Param("produtoId") UUID produtoId);
}
