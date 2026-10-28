package com.gestaocasas.application.usecase.conta;

import com.gestaocasas.application.dto.ContaResponse;
import com.gestaocasas.application.dto.EstornoRequest;
import com.gestaocasas.application.dto.LancamentoRequest;
import com.gestaocasas.application.service.ContaReserva;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.application.usecase.reserva.AcoesReserva;
import com.gestaocasas.domain.entity.LancamentoConta;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ResourceNotFoundException;
import com.gestaocasas.domain.repository.LancamentoContaRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF-38 e RN-06: consumo na conta do quarto, só com o hóspede na casa. Com produto, o preço vem do cadastro e
 * o estoque do imóvel da reserva é baixado; o estorno devolve ao estoque.
 */
@Component
public class LancamentoContaUseCase {

    private final LancamentoContaRepository repository;
    private final ReservaRepository reservaRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentadorEstoque movimentador;
    private final AcoesReserva acoes;
    private final UsuarioLogado usuarioLogado;
    private final ContaReserva conta;

    public LancamentoContaUseCase(LancamentoContaRepository repository, ReservaRepository reservaRepository,
                                  ProdutoRepository produtoRepository, MovimentadorEstoque movimentador,
                                  AcoesReserva acoes, UsuarioLogado usuarioLogado, ContaReserva conta) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.produtoRepository = produtoRepository;
        this.movimentador = movimentador;
        this.acoes = acoes;
        this.usuarioLogado = usuarioLogado;
        this.conta = conta;
    }

    @Transactional
    public ContaResponse lancar(UUID reservaId, LancamentoRequest request) {
        Reserva reserva = acoes.buscar(reservaId);
        if (reserva.getStatus() != StatusReserva.HOSPEDADO) {
            throw new RegraDeNegocioException("RN-06", "Só se lança na conta de reservas com status Hospedado.");
        }
        LancamentoConta lancamento = new LancamentoConta();
        lancamento.setReserva(reserva);
        lancamento.setData(acoes.hoje());
        lancamento.setQuantidade(request.quantidade());
        String descricao = request.descricao() == null || request.descricao().isBlank() ? null
                : request.descricao().trim();
        if (request.produtoId() != null) {
            Produto produto = produtoRepository.findById(request.produtoId())
                    .orElseThrow(() -> new ProdutoNotFoundException(request.produtoId()));
            if (!produto.isAtivo()) {
                throw new RegraDeNegocioException("O produto '" + produto.getNome() + "' está inativo");
            }
            lancamento.setProduto(produto);
            lancamento.setBaixouEstoque(produto.isControlaEstoque());
            lancamento.setValorUnitario(produto.getPrecoVenda());
            lancamento.setDescricao(descricao == null ? produto.getNome() : descricao);
        } else {
            if (descricao == null) {
                throw new CampoInvalidoException("descricao", "Informe a descrição do lançamento.");
            }
            if (request.valorUnitario() == null) {
                throw new CampoInvalidoException("valorUnitario", "Informe o valor do lançamento.");
            }
            lancamento.setValorUnitario(request.valorUnitario());
            lancamento.setDescricao(descricao);
        }
        lancamento.setTotal(lancamento.getValorUnitario().multiply(BigDecimal.valueOf(request.quantidade())));
        lancamento.setUsuario(usuarioLogado.atual().orElse(null));
        repository.saveAndFlush(lancamento);
        if (lancamento.isBaixouEstoque()) {
            movimentador.movimentar(reserva.getImovel().getId(), lancamento.getProdutoId(),
                    TipoMovimentacao.SAIDA_VENDA, lancamento.getQuantidade(), "Conta da reserva " + reserva.getCodigo(),
                    TipoReferenciaMovimentacao.LANCAMENTO_CONTA, lancamento.getId());
        }
        acoes.registrar(reserva, "Lançamento: " + lancamento.getQuantidade() + "× " + lancamento.getDescricao());
        reservaRepository.saveAndFlush(reserva);
        return conta.calcular(reserva);
    }

    /** RF-40. RN-14 (só gerente) passa a valer quando houver perfis; o motivo e o usuário já ficam registrados. */
    @Transactional
    public ContaResponse estornar(UUID reservaId, UUID lancamentoId, EstornoRequest request) {
        Reserva reserva = acoes.buscar(reservaId);
        LancamentoConta lancamento = repository.findById(lancamentoId)
                .filter(l -> l.getReserva().getId().equals(reservaId))
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado nesta conta: " + lancamentoId));
        if (lancamento.isEstornado()) {
            throw new RegraDeNegocioException("Este lançamento já foi estornado.");
        }
        lancamento.setEstornado(true);
        lancamento.setMotivoEstorno(request.motivo().trim());
        lancamento.setEstornadoEm(acoes.agora());
        lancamento.setEstornadoPor(usuarioLogado.atual().orElse(null));
        repository.saveAndFlush(lancamento);
        if (lancamento.isBaixouEstoque()) {
            movimentador.movimentar(reserva.getImovel().getId(), lancamento.getProdutoId(), TipoMovimentacao.DEVOLUCAO,
                    lancamento.getQuantidade(), "Estorno na conta da reserva " + reserva.getCodigo(),
                    TipoReferenciaMovimentacao.LANCAMENTO_CONTA, lancamento.getId());
        }
        acoes.registrar(reserva, "Estorno de " + lancamento.getDescricao() + ": " + lancamento.getMotivoEstorno());
        reservaRepository.saveAndFlush(reserva);
        return conta.calcular(reserva);
    }
}
