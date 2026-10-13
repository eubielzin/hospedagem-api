package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.CancelamentoVendaRequest;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Component
public class CancelarVendaUseCase {

    private final VendaRepository vendaRepository;
    private final MovimentadorEstoque movimentador;
    private final VendaMapper mapper;

    public CancelarVendaUseCase(VendaRepository vendaRepository, MovimentadorEstoque movimentador,
                                VendaMapper mapper) {
        this.vendaRepository = vendaRepository;
        this.movimentador = movimentador;
        this.mapper = mapper;
    }

    /**
     * Devolve ao estoque do imóvel da venda o que ainda não tinha sido devolvido e marca a venda
     * como cancelada. Cancelar uma venda já cancelada não faz nada e devolve o estado atual.
     */
    @Transactional
    public VendaResponse execute(UUID vendaId, CancelamentoVendaRequest request) {
        Venda venda = vendaRepository.findByIdForUpdate(vendaId).orElseThrow(() -> new VendaNotFoundException(vendaId));
        if (venda.getStatus() == StatusVenda.CANCELADA) {
            return mapper.toResponse(venda);
        }
        String motivo = request.motivo().trim();
        UUID imovelId = venda.getImovel().getId();
        venda.getItens().stream()
                .filter(item -> item.isBaixouEstoque() && item.quantidadeNaoDevolvida() > 0)
                .sorted(Comparator.comparing((ItemVenda item) -> item.getProduto().getId()))
                .forEach(item -> movimentador.movimentar(imovelId, item.getProduto().getId(),
                        TipoMovimentacao.DEVOLUCAO, item.quantidadeNaoDevolvida(),
                        limitar("Cancelamento da venda: " + motivo),
                        TipoReferenciaMovimentacao.VENDA, venda.getId()));
        venda.cancelar(motivo);
        return mapper.toResponse(vendaRepository.saveAndFlush(venda));
    }

    static String limitar(String texto) {
        return texto.length() <= 300 ? texto : texto.substring(0, 300);
    }
}
