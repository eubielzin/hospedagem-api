package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.ItemVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaRequest;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.Idempotencia;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.enums.TipoVenda;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RegistrarVendaUseCase {

    private final VendaRepository vendaRepository;
    private final ImovelRepository imovelRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentadorEstoque movimentador;
    private final Idempotencia idempotencia;
    private final VendaMapper mapper;

    public RegistrarVendaUseCase(VendaRepository vendaRepository, ImovelRepository imovelRepository,
                                 ProdutoRepository produtoRepository, MovimentadorEstoque movimentador,
                                 Idempotencia idempotencia, VendaMapper mapper) {
        this.vendaRepository = vendaRepository;
        this.imovelRepository = imovelRepository;
        this.produtoRepository = produtoRepository;
        this.movimentador = movimentador;
        this.idempotencia = idempotencia;
        this.mapper = mapper;
    }

    /**
     * Venda, itens, baixa de estoque e movimentações são gravados em uma única transação: se
     * faltar saldo para um item, nada é gravado. Os preços vêm do cadastro, nunca da requisição.
     */
    @Transactional
    public ResultadoIdempotente<VendaResponse> execute(VendaRequest request, String chaveIdempotencia) {
        Optional<UUID> vendaAnterior = idempotencia.iniciar(OperacaoIdempotente.VENDA, chaveIdempotencia,
                impressaoDigital(request));
        if (vendaAnterior.isPresent()) {
            UUID id = vendaAnterior.get();
            return ResultadoIdempotente.repetido(mapper.toResponse(
                    vendaRepository.findById(id).orElseThrow(() -> new VendaNotFoundException(id))));
        }

        UUID imovelId = request.imovelId();
        Imovel imovel = imovelRepository.findById(imovelId).orElseThrow(() -> new ImovelNotFoundException(imovelId));
        if (imovel.getStatus() == StatusImovel.INATIVO) {
            throw new RegraDeNegocioException("O imóvel '" + imovel.getNome() + "' está inativo");
        }

        Set<UUID> produtoIds = new HashSet<>();
        for (ItemVendaRequest item : request.itens()) {
            if (!produtoIds.add(item.produtoId())) {
                throw new RegraDeNegocioException("O produto " + item.produtoId()
                        + " aparece mais de uma vez nos itens; some as quantidades em um único item");
            }
        }
        Map<UUID, Produto> produtos = produtoRepository.findAllById(produtoIds).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));

        Venda venda = new Venda();
        venda.setImovel(imovel);
        venda.setTipo(TipoVenda.AVULSA);
        venda.setStatus(StatusVenda.CONCLUIDA);
        // Não há informação de pagamento validada: a venda nunca nasce como recebida.
        venda.setPagamentoStatus(StatusPagamento.PENDENTE);
        venda.setObservacao(request.observacao() == null || request.observacao().isBlank()
                ? null : request.observacao().trim());

        BigDecimal total = BigDecimal.ZERO;
        for (ItemVendaRequest itemRequest : request.itens()) {
            Produto produto = produtos.get(itemRequest.produtoId());
            if (produto == null) {
                throw new ProdutoNotFoundException(itemRequest.produtoId());
            }
            if (!produto.isAtivo()) {
                throw new RegraDeNegocioException("O produto '" + produto.getNome() + "' está inativo");
            }
            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setProdutoCodigo(produto.getCodigo());
            item.setProdutoNome(produto.getNome());
            item.setBaixouEstoque(produto.isControlaEstoque());
            item.setQuantidade(itemRequest.quantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setSubtotal(produto.getPrecoVenda().multiply(BigDecimal.valueOf(itemRequest.quantidade())));
            venda.adicionarItem(item);
            total = total.add(item.getSubtotal());
        }
        venda.setTotal(total);
        if (request.totalEsperado() != null && request.totalEsperado().compareTo(total) != 0) {
            throw new ConflitoException("O total da venda é " + total + ", diferente do total esperado "
                    + request.totalEsperado() + ". Os preços podem ter mudado: atualize os itens e tente de novo.");
        }

        vendaRepository.save(venda);
        // Baixa somente do imóvel da venda. Ordem fixa de produto: duas vendas simultâneas com os
        // mesmos itens bloqueiam os saldos na mesma sequência e não travam uma à outra.
        venda.getItens().stream()
                .filter(ItemVenda::isBaixouEstoque)
                .sorted(Comparator.comparing(item -> item.getProduto().getId()))
                .forEach(item -> movimentador.movimentar(imovelId, item.getProduto().getId(),
                        TipoMovimentacao.SAIDA_VENDA, item.getQuantidade(), "Venda",
                        TipoReferenciaMovimentacao.VENDA, venda.getId()));
        vendaRepository.flush();

        idempotencia.concluir(OperacaoIdempotente.VENDA, chaveIdempotencia, venda.getId());
        return ResultadoIdempotente.novo(mapper.toResponse(venda));
    }

    private static String impressaoDigital(VendaRequest request) {
        String itens = request.itens().stream()
                .map(item -> item.produtoId() + ":" + item.quantidade())
                .sorted()
                .collect(Collectors.joining(","));
        return String.join("|", String.valueOf(request.imovelId()), itens, String.valueOf(request.observacao()),
                request.totalEsperado() == null ? "" : request.totalEsperado().stripTrailingZeros().toPlainString());
    }
}
