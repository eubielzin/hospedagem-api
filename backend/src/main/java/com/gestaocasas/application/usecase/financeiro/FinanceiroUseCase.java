package com.gestaocasas.application.usecase.financeiro;

import com.gestaocasas.application.dto.FluxoCaixaResponse;
import com.gestaocasas.application.dto.FluxoCaixaResponse.Linha;
import com.gestaocasas.application.dto.FluxoCaixaResponse.Tipo;
import com.gestaocasas.application.dto.PagamentoResponse;
import com.gestaocasas.application.dto.PendenciaResponse;
import com.gestaocasas.application.dto.RecebimentoRepasseRequest;
import com.gestaocasas.application.dto.RepasseResponse;
import com.gestaocasas.application.mapper.PagamentoMapper;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.Repasses;
import com.gestaocasas.domain.entity.Despesa;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.TipoCanal;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.DespesaRepository;
import com.gestaocasas.domain.repository.PagamentoRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import com.gestaocasas.domain.repository.SaldoReserva;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recebimentos (RF-44), repasses das OTAs (RF-46, RN-09), pendências (RF-47) e fluxo de caixa (RF-50, RN-08). */
@Component
public class FinanceiroUseCase {

    private final PagamentoRepository pagamentoRepository;
    private final ReservaRepository reservaRepository;
    private final DespesaRepository despesaRepository;
    private final PagamentoMapper mapper;

    public FinanceiroUseCase(PagamentoRepository pagamentoRepository, ReservaRepository reservaRepository,
                             DespesaRepository despesaRepository, PagamentoMapper mapper) {
        this.pagamentoRepository = pagamentoRepository;
        this.reservaRepository = reservaRepository;
        this.despesaRepository = despesaRepository;
        this.mapper = mapper;
    }

    /** Pagamentos de reservas e de vendas avulsas com data em [inicio, fim], o mais recente primeiro. */
    @Transactional(readOnly = true)
    public List<PagamentoResponse> recebimentos(LocalDate inicio, LocalDate fim) {
        exigirPeriodo(inicio, fim);
        return pagamentoRepository.findNoPeriodo(inicio, fim).stream().map(mapper::toResponse).toList();
    }

    /** Um repasse por reserva ativa de OTA, pela data prevista. */
    @Transactional(readOnly = true)
    public List<RepasseResponse> repasses() {
        return reservaRepository.findComRepasse().stream().map(Repasses::de).toList();
    }

    @Transactional
    public RepasseResponse receberRepasse(UUID reservaId, RecebimentoRepasseRequest request) {
        Reserva reserva = reservaRepository.findDetalhadaById(reservaId)
                .orElseThrow(() -> new ReservaNotFoundException(reservaId));
        if (reserva.getCanal().getTipo() != TipoCanal.OTA || !reserva.getStatus().ativa()) {
            throw new RegraDeNegocioException("RN-09", "A reserva " + reserva.getCodigo()
                    + " não tem repasse de OTA a receber.");
        }
        if (reserva.getRepasseRecebidoEm() != null) {
            throw new RegraDeNegocioException("O repasse da reserva " + reserva.getCodigo() + " já foi recebido em "
                    + Formato.data(reserva.getRepasseRecebidoEm()) + ".");
        }
        reserva.setRepasseRecebidoEm(request.data());
        return Repasses.de(reservaRepository.saveAndFlush(reserva));
    }

    /** RF-47: contas com saldo de reservas hospedadas ou com check-out feito, do maior saldo para o menor. */
    @Transactional(readOnly = true)
    public List<PendenciaResponse> pendencias() {
        Map<UUID, BigDecimal> saldos = reservaRepository.findSaldosEmAberto().stream()
                .collect(Collectors.toMap(SaldoReserva::getReservaId, SaldoReserva::getSaldo));
        if (saldos.isEmpty()) {
            return List.of();
        }
        return reservaRepository.findDetalhadasByIdIn(saldos.keySet()).stream()
                .map(r -> new PendenciaResponse(r.getId(), r.getCodigo(), r.getTitular().getNome(),
                        r.getImovel().getNome(), r.getStatus().rotulo(), saldos.get(r.getId()).setScale(2)))
                .sorted(Comparator.comparing(PendenciaResponse::saldo).reversed()
                        .thenComparing(PendenciaResponse::codigoReserva))
                .toList();
    }

    /**
     * RF-50. Entradas: pagamentos (líquido), repasses (recebidos ou previstos) e valores retidos de encerramentos;
     * saídas: despesas (pagas ou a vencer). RN-08: o valor de reserva cancelada nunca entra, só o retido.
     */
    @Transactional(readOnly = true)
    public FluxoCaixaResponse fluxo(LocalDate inicio, LocalDate fim) {
        exigirPeriodo(inicio, fim);
        List<Linha> linhas = new ArrayList<>();
        for (Pagamento p : pagamentoRepository.findNoPeriodo(inicio, fim)) {
            linhas.add(new Linha(p.getData(), p.getDescricao() + " (líquido)", Tipo.ENTRADA, p.getValorLiquido(), true));
        }
        for (Reserva r : reservaRepository.findRepassesNoPeriodo(inicio, fim)) {
            RepasseResponse repasse = Repasses.de(r);
            boolean recebido = repasse.recebidoEm() != null;
            linhas.add(new Linha(recebido ? repasse.recebidoEm() : repasse.previsao(),
                    "Repasse " + repasse.canalNome() + " – " + repasse.codigoReserva(), Tipo.ENTRADA,
                    repasse.valorLiquido(), recebido));
        }
        for (Reserva r : reservaRepository.findComValorRetidoNoPeriodo(inicio, fim)) {
            linhas.add(new Linha(r.getEncerramento().getData(), "Valor retido – " + r.getCodigo(), Tipo.ENTRADA,
                    r.getEncerramento().getValorRetido(), true));
        }
        for (Despesa d : despesaRepository.findNoFluxo(inicio, fim)) {
            boolean paga = d.getDataPagamento() != null;
            linhas.add(new Linha(paga ? d.getDataPagamento() : d.getVencimento(),
                    d.getCategoria() + " – " + d.getFornecedor(), Tipo.SAIDA, d.getValor(), paga));
        }
        linhas.sort(Comparator.comparing(Linha::data).thenComparing(Linha::tipo).thenComparing(Linha::descricao));

        Function<Tipo, Function<Boolean, BigDecimal>> somar = tipo -> realizado -> linhas.stream()
                .filter(l -> l.tipo() == tipo && l.realizado() == realizado)
                .map(Linha::valor)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal entradasRealizadas = somar.apply(Tipo.ENTRADA).apply(true);
        BigDecimal saidasRealizadas = somar.apply(Tipo.SAIDA).apply(true);
        BigDecimal entradasPrevistas = somar.apply(Tipo.ENTRADA).apply(false);
        BigDecimal saidasPrevistas = somar.apply(Tipo.SAIDA).apply(false);
        BigDecimal saldoRealizado = entradasRealizadas.subtract(saidasRealizadas);
        return new FluxoCaixaResponse(inicio, fim, entradasRealizadas, saidasRealizadas, entradasPrevistas,
                saidasPrevistas, saldoRealizado, saldoRealizado.add(entradasPrevistas).subtract(saidasPrevistas), linhas);
    }

    private static void exigirPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new CampoInvalidoException("fim", "O fim deve ser igual ou depois do início.");
        }
    }
}
