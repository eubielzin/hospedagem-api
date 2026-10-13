package com.gestaocasas;

import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoVenda;

import java.math.BigDecimal;
import java.util.UUID;

public final class VendaFixtures {

    private VendaFixtures() {
    }

    public static Produto produto(UUID id, String nome, String preco, boolean controlaEstoque) {
        Produto produto = ProdutoFixtures.produto(id, true);
        produto.setCodigo("COD-" + nome);
        produto.setNome(nome);
        produto.setPrecoVenda(new BigDecimal(preco));
        produto.setControlaEstoque(controlaEstoque);
        return produto;
    }

    public static Venda venda(UUID id, UUID imovelId) {
        Venda venda = new Venda();
        venda.setId(id);
        venda.setImovel(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO));
        venda.setTipo(TipoVenda.AVULSA);
        venda.setStatus(StatusVenda.CONCLUIDA);
        venda.setPagamentoStatus(StatusPagamento.PENDENTE);
        venda.setTotal(BigDecimal.ZERO.setScale(2));
        return venda;
    }

    public static ItemVenda item(Venda venda, Produto produto, int quantidade, int devolvida) {
        ItemVenda item = new ItemVenda();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setProdutoCodigo(produto.getCodigo());
        item.setProdutoNome(produto.getNome());
        item.setBaixouEstoque(produto.isControlaEstoque());
        item.setQuantidade(quantidade);
        item.setQuantidadeDevolvida(devolvida);
        item.setPrecoUnitario(produto.getPrecoVenda());
        item.setSubtotal(produto.getPrecoVenda().multiply(BigDecimal.valueOf(quantidade)));
        venda.adicionarItem(item);
        venda.setTotal(venda.getTotal().add(item.getSubtotal()));
        return item;
    }
}
