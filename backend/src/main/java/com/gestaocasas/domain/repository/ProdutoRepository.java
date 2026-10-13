package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, UUID>, JpaSpecificationExecutor<Produto> {

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdNot(String codigoBarras, UUID id);

    /**
     * Busca por parte do nome ou do código, ou pelo início do código de barras, sem diferenciar
     * maiúsculas nem acentos. Filtro, ordenação e limite são aplicados no banco.
     * <p>
     * O saldo vem somente do imóvel informado. Sem imóvel, nenhum saldo é devolvido. Ordem: produtos
     * disponíveis no imóvel primeiro; depois código exato, nome que começa pelo termo, palavra do
     * nome que começa pelo termo e, por fim, as demais ocorrências.
     *
     * @param termo termo já com os curingas de LIKE escapados com "!"
     */
    @Query(value = """
            SELECT p.id AS "id", p.codigo AS "codigo", p.codigo_barras AS "codigoBarras", p.nome AS "nome",
                   p.categoria AS "categoria", p.unidade AS "unidade", p.preco_venda AS "precoVenda",
                   p.controla_estoque AS "controlaEstoque", p.ativo AS "ativo",
                   CASE WHEN p.controla_estoque AND CAST(:temImovel AS boolean)
                        THEN coalesce(e.quantidade_atual, 0) END AS "quantidadeDisponivel"
            FROM produto p
            CROSS JOIN (SELECT normalizar_busca(:termo) AS termo) b
            LEFT JOIN estoque_imovel e ON e.produto_id = p.id AND e.imovel_id = CAST(:imovelId AS uuid)
            WHERE (p.nome_busca LIKE '%' || b.termo || '%' ESCAPE '!'
                OR p.codigo_busca LIKE '%' || b.termo || '%' ESCAPE '!'
                OR lower(p.codigo_barras) LIKE b.termo || '%' ESCAPE '!')
              AND (p.ativo OR NOT CAST(:somenteAtivos AS boolean))
            ORDER BY (NOT CAST(:temImovel AS boolean) OR NOT p.controla_estoque
                      OR coalesce(e.quantidade_atual, 0) > 0) DESC,
                     CASE WHEN p.codigo_busca = b.termo OR lower(p.codigo_barras) = b.termo THEN 0
                          WHEN p.nome_busca LIKE b.termo || '%' ESCAPE '!' THEN 1
                          WHEN p.nome_busca LIKE '% ' || b.termo || '%' ESCAPE '!' THEN 2
                          ELSE 3 END,
                     p.nome_busca, p.id
            LIMIT :limite
            """, nativeQuery = true)
    List<ProdutoBuscaView> buscar(@Param("termo") String termo, @Param("imovelId") UUID imovelId,
                                  @Param("temImovel") boolean temImovel,
                                  @Param("somenteAtivos") boolean somenteAtivos, @Param("limite") int limite);
}
