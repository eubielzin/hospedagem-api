package com.gestaocasas.application.usecase.conta;

import com.gestaocasas.application.dto.ContaResponse;
import com.gestaocasas.application.dto.PagamentoRequest;
import com.gestaocasas.application.service.ContaReserva;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.application.usecase.reserva.AcoesReserva;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.PagamentoRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** RF-44: recebimento na conta da reserva. valorLiquido = bruto − taxa; a data é hoje. */
@Component
public class RegistrarPagamentoReservaUseCase {

    private final PagamentoRepository repository;
    private final ReservaRepository reservaRepository;
    private final AcoesReserva acoes;
    private final UsuarioLogado usuarioLogado;
    private final ContaReserva conta;

    public RegistrarPagamentoReservaUseCase(PagamentoRepository repository, ReservaRepository reservaRepository,
                                            AcoesReserva acoes, UsuarioLogado usuarioLogado, ContaReserva conta) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.acoes = acoes;
        this.usuarioLogado = usuarioLogado;
        this.conta = conta;
    }

    @Transactional
    public ContaResponse execute(UUID reservaId, PagamentoRequest request) {
        Reserva reserva = acoes.buscar(reservaId);
        if (!reserva.getStatus().ativa()) {
            throw new RegraDeNegocioException("RN-03", "A reserva está encerrada (" + reserva.getStatus().rotulo()
                    + "): não recebe pagamentos.");
        }
        BigDecimal taxa = request.taxa() == null ? BigDecimal.ZERO : request.taxa();
        if (taxa.compareTo(request.valorBruto()) > 0) {
            throw new CampoInvalidoException("taxa", "A taxa não pode passar do valor recebido.");
        }
        Pagamento pagamento = new Pagamento();
        pagamento.setReserva(reserva);
        pagamento.setData(acoes.hoje());
        pagamento.setForma(request.forma());
        pagamento.setValorBruto(request.valorBruto());
        pagamento.setTaxa(taxa);
        pagamento.setValorLiquido(request.valorBruto().subtract(taxa));
        pagamento.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? "Recebimento da reserva " + reserva.getCodigo() : request.descricao().trim());
        pagamento.setUsuario(usuarioLogado.atual().orElse(null));
        repository.saveAndFlush(pagamento);
        acoes.registrar(reserva, "Recebimento de " + Formato.dinheiro(request.valorBruto()) + " ("
                + request.forma().rotulo() + ")");
        reservaRepository.saveAndFlush(reserva);
        return conta.calcular(reserva);
    }
}
