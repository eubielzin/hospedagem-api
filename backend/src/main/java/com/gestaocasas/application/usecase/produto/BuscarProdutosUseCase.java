package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoBuscaResponse;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoBuscaView;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class BuscarProdutosUseCase {

    private final ProdutoRepository produtoRepository;
    private final ImovelRepository imovelRepository;
    private final int limiteMaximo;

    public BuscarProdutosUseCase(ProdutoRepository produtoRepository, ImovelRepository imovelRepository,
                                 @Value("${app.produtos.busca.limite-maximo:50}") int limiteMaximo) {
        this.produtoRepository = produtoRepository;
        this.imovelRepository = imovelRepository;
        this.limiteMaximo = limiteMaximo;
    }

    /** @param imovelId imóvel cujo saldo deve acompanhar os resultados; {@code null} para não trazer saldo */
    @Transactional(readOnly = true)
    public List<ProdutoBuscaResponse> execute(String termo, UUID imovelId, boolean somenteAtivos, int limite) {
        if (termo == null || termo.isBlank()) {
            throw new IllegalArgumentException("Termo é obrigatório");
        }
        if (imovelId != null && !imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        int limiteEfetivo = Math.max(1, Math.min(limite, limiteMaximo));
        return produtoRepository.buscar(escapeLike(termo.trim()), imovelId, imovelId != null, somenteAtivos,
                        limiteEfetivo)
                .stream().map(BuscarProdutosUseCase::toResponse).toList();
    }

    private static ProdutoBuscaResponse toResponse(ProdutoBuscaView produto) {
        Integer quantidade = produto.getQuantidadeDisponivel();
        return new ProdutoBuscaResponse(produto.getId(), produto.getCodigo(), produto.getCodigoBarras(),
                produto.getNome(), CategoriaProduto.valueOf(produto.getCategoria()), produto.getUnidade(),
                produto.getPrecoVenda(), produto.getControlaEstoque(), produto.getAtivo(), quantidade,
                quantidade != null && quantidade == 0);
    }

    // A consulta usa ESCAPE '!': "%" e "_" digitados pelo usuário são procurados como texto.
    private static String escapeLike(String valor) {
        return valor.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
