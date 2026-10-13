package com.gestaocasas.application.mapper;

import com.gestaocasas.application.dto.ItemVendaResponse;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.dto.VendaResumoResponse;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Venda;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class VendaMapper {

    public VendaResponse toResponse(Venda venda) {
        List<ItemVendaResponse> itens = venda.getItens().stream().map(VendaMapper::toResponse).toList();
        BigDecimal totalDevolvido = venda.getItens().stream()
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidadeDevolvida())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VendaResponse(venda.getId(), venda.getImovel().getId(), venda.getTipo(), venda.getStatus(),
                venda.getPagamentoStatus(), venda.getTotal(), totalDevolvido.setScale(2), venda.getObservacao(),
                itens, venda.getCriadoEm(), venda.getCanceladoEm(), venda.getMotivoCancelamento());
    }

    public VendaResumoResponse toResumo(Venda venda) {
        return new VendaResumoResponse(venda.getId(), venda.getImovel().getId(), venda.getTipo(), venda.getStatus(),
                venda.getPagamentoStatus(), venda.getTotal(), venda.getCriadoEm(), venda.getCanceladoEm());
    }

    private static ItemVendaResponse toResponse(ItemVenda item) {
        return new ItemVendaResponse(item.getId(), item.getProduto().getId(), item.getProdutoCodigo(),
                item.getProdutoNome(), item.getQuantidade(), item.getQuantidadeDevolvida(), item.getPrecoUnitario(),
                item.getSubtotal());
    }
}
