package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.DevolucaoVendaRequest;
import com.gestaocasas.application.dto.ItemVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.Idempotencia;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DevolverItensVendaUseCase {

    private final VendaRepository vendaRepository;
    private final MovimentadorEstoque movimentador;
    private final Idempotencia idempotencia;
    private final VendaMapper mapper;

    public DevolverItensVendaUseCase(VendaRepository vendaRepository, MovimentadorEstoque movimentador,
                                     Idempotencia idempotencia, VendaMapper mapper) {
        this.vendaRepository = vendaRepository;
        this.movimentador = movimentador;
        this.idempotencia = idempotencia;
        this.mapper = mapper;
    }

    /**
     * Devolução física: os produtos voltam para o estoque do imóvel da venda. Não registra
     * reembolso, que depende do módulo financeiro.
     */
    @Transactional
    public ResultadoIdempotente<VendaResponse> execute(UUID vendaId, DevolucaoVendaRequest request,
                                                       String chaveIdempotencia) {
        boolean repetida = idempotencia.iniciar(OperacaoIdempotente.DEVOLUCAO_VENDA, chaveIdempotencia,
                impressaoDigital(vendaId, request)).isPresent();
        Venda venda = vendaRepository.findByIdForUpdate(vendaId).orElseThrow(() -> new VendaNotFoundException(vendaId));
        if (repetida) {
            return ResultadoIdempotente.repetido(mapper.toResponse(venda));
        }
        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new RegraDeNegocioException("A venda está cancelada: os itens já voltaram ao estoque");
        }

        Set<UUID> produtoIds = new HashSet<>();
        List<ItemVendaRequest> devolucoes = request.itens().stream()
                .sorted(Comparator.comparing(ItemVendaRequest::produtoId)).toList();
        String motivo = CancelarVendaUseCase.limitar("Devolução da venda: " + request.motivo().trim());
        UUID imovelId = venda.getImovel().getId();
        for (ItemVendaRequest devolucao : devolucoes) {
            if (!produtoIds.add(devolucao.produtoId())) {
                throw new RegraDeNegocioException("O produto " + devolucao.produtoId()
                        + " aparece mais de uma vez nos itens; some as quantidades em um único item");
            }
            ItemVenda item = venda.getItens().stream()
                    .filter(candidato -> candidato.getProduto().getId().equals(devolucao.produtoId()))
                    .findFirst()
                    .orElseThrow(() -> new RegraDeNegocioException("O produto " + devolucao.produtoId()
                            + " não faz parte desta venda"));
            if (!item.isBaixouEstoque()) {
                throw new RegraDeNegocioException("'" + item.getProdutoNome()
                        + "' não é um produto físico: não há o que devolver ao estoque");
            }
            if (devolucao.quantidade() > item.quantidadeNaoDevolvida()) {
                throw new RegraDeNegocioException("Não é possível devolver " + devolucao.quantidade() + " de '"
                        + item.getProdutoNome() + "': vendido " + item.getQuantidade() + ", já devolvido "
                        + item.getQuantidadeDevolvida());
            }
            item.setQuantidadeDevolvida(item.getQuantidadeDevolvida() + devolucao.quantidade());
            movimentador.movimentar(imovelId, devolucao.produtoId(), TipoMovimentacao.DEVOLUCAO,
                    devolucao.quantidade(), motivo, TipoReferenciaMovimentacao.VENDA, venda.getId());
        }
        vendaRepository.saveAndFlush(venda);

        idempotencia.concluir(OperacaoIdempotente.DEVOLUCAO_VENDA, chaveIdempotencia, venda.getId());
        return ResultadoIdempotente.novo(mapper.toResponse(venda));
    }

    private static String impressaoDigital(UUID vendaId, DevolucaoVendaRequest request) {
        String itens = request.itens().stream()
                .map(item -> item.produtoId() + ":" + item.quantidade())
                .sorted()
                .collect(Collectors.joining(","));
        return String.join("|", vendaId.toString(), itens, request.motivo());
    }
}
