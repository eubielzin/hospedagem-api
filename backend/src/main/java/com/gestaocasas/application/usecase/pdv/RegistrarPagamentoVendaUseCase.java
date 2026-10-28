package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.application.dto.PagamentoVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.Idempotencia;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.FormaPagamento;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.PagamentoRepository;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RF-39: pagamento de uma venda avulsa, de uma vez, pelo total menos o devolvido. Entra nos recebimentos do
 * financeiro como pagamento sem reserva.
 */
@Component
public class RegistrarPagamentoVendaUseCase {

    private final VendaRepository vendaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final Idempotencia idempotencia;
    private final UsuarioLogado usuarioLogado;
    private final VendaMapper mapper;
    private final Clock clock;

    public RegistrarPagamentoVendaUseCase(VendaRepository vendaRepository, PagamentoRepository pagamentoRepository,
                                          Idempotencia idempotencia, UsuarioLogado usuarioLogado, VendaMapper mapper,
                                          Clock clock) {
        this.vendaRepository = vendaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.idempotencia = idempotencia;
        this.usuarioLogado = usuarioLogado;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ResultadoIdempotente<VendaResponse> execute(UUID vendaId, PagamentoVendaRequest request,
                                                       String chaveIdempotencia) {
        boolean repetida = idempotencia.iniciar(OperacaoIdempotente.PAGAMENTO_VENDA, chaveIdempotencia,
                impressaoDigital(vendaId, request)).isPresent();
        Venda venda = vendaRepository.findByIdForUpdate(vendaId).orElseThrow(() -> new VendaNotFoundException(vendaId));
        if (repetida) {
            return ResultadoIdempotente.repetido(mapper.toResponse(venda));
        }
        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new RegraDeNegocioException("A venda está cancelada: não há o que receber");
        }
        if (venda.getPagamentoStatus() == StatusPagamento.RECEBIDO) {
            throw new RegraDeNegocioException("O pagamento desta venda já foi registrado");
        }
        BigDecimal valor = venda.getTotal().subtract(venda.getItens().stream()
                .map(item -> item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidadeDevolvida())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (valor.signum() <= 0) {
            throw new RegraDeNegocioException("A venda não tem valor a receber: todos os itens foram devolvidos");
        }
        BigDecimal recebido = request.valorRecebido();
        if (request.formaPagamento() == FormaPagamento.DINHEIRO) {
            if (recebido == null || recebido.compareTo(valor) < 0) {
                throw new CampoInvalidoException("valorRecebido", "Informe o valor recebido, de pelo menos "
                        + Formato.dinheiro(valor) + ".");
            }
        } else {
            recebido = null;
        }
        BigDecimal taxa = request.taxa() == null ? BigDecimal.ZERO : request.taxa();
        if (taxa.compareTo(valor) > 0) {
            throw new CampoInvalidoException("taxa", "A taxa não pode passar do valor da venda.");
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setVenda(venda);
        pagamento.setData(LocalDate.now(clock));
        pagamento.setForma(request.formaPagamento());
        pagamento.setValorBruto(valor);
        pagamento.setTaxa(taxa);
        pagamento.setValorLiquido(valor.subtract(taxa));
        pagamento.setValorRecebido(recebido);
        pagamento.setDescricao(request.observacao() == null || request.observacao().isBlank()
                ? "Venda " + venda.getImovel().getNome() : request.observacao().trim());
        pagamento.setUsuario(usuarioLogado.atual().orElse(null));
        pagamentoRepository.saveAndFlush(pagamento);
        venda.setPagamento(pagamento);
        venda.setPagamentoStatus(StatusPagamento.RECEBIDO);
        vendaRepository.saveAndFlush(venda);

        idempotencia.concluir(OperacaoIdempotente.PAGAMENTO_VENDA, chaveIdempotencia, venda.getId());
        return ResultadoIdempotente.novo(mapper.toResponse(venda));
    }

    private static String impressaoDigital(UUID vendaId, PagamentoVendaRequest request) {
        return String.join("|", vendaId.toString(), request.formaPagamento().name(),
                String.valueOf(request.valorRecebido()), String.valueOf(request.taxa()),
                String.valueOf(request.observacao()));
    }
}
