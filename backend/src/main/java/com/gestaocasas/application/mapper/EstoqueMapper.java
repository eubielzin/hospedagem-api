package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.Produto;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EstoqueMapper {

    /** @param estoque saldo do produto no imóvel, ou {@code null} se ele nunca teve o produto (saldo zero) */
    public EstoqueItemResponse toResponse(UUID imovelId, Produto produto, EstoqueImovel estoque) {
        int quantidade = estoque == null ? 0 : estoque.getQuantidadeAtual();
        int minimo = estoque == null ? 0 : estoque.getEstoqueMinimo();
        return new EstoqueItemResponse(imovelId, produto.getId(), produto.getCodigo(), produto.getCodigoBarras(),
                produto.getNome(), produto.getUnidade(), produto.isAtivo(), quantidade, minimo,
                EstoqueImovel.abaixoDoMinimo(quantidade, minimo),
                estoque == null ? null : estoque.getAtualizadoEm());
    }
}
