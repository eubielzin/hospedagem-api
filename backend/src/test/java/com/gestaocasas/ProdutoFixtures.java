package com.gestaocasas;

import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.enums.CategoriaProduto;

import java.math.BigDecimal;
import java.util.UUID;

public final class ProdutoFixtures {

    private ProdutoFixtures() {
    }

    public static ProdutoRequest request(Boolean ativo) {
        return new ProdutoRequest(" COCA350 ", " 7894900010015 ", " Coca-Cola Lata 350ml ", "  ",
                CategoriaProduto.CONSUMO, " un ", new BigDecimal("3.20"), new BigDecimal("6.00"), true, ativo);
    }

    public static Produto produto(UUID id, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setCodigo("AGUA500");
        produto.setNome("Água Mineral 500ml");
        produto.setCategoria(CategoriaProduto.CONSUMO);
        produto.setUnidade("UN");
        produto.setPrecoVenda(new BigDecimal("4.00"));
        produto.setControlaEstoque(true);
        produto.setAtivo(ativo);
        return produto;
    }
}
