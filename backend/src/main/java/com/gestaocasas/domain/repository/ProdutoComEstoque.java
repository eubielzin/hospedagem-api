package com.gestaocasas.domain.repository;

import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.Produto;

/** Produto e o seu saldo em um imóvel; {@code estoque} é nulo quando o imóvel nunca teve o produto. */
public interface ProdutoComEstoque {

    Produto getProduto();

    EstoqueImovel getEstoque();
}
