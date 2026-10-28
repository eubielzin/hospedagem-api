package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.ContaResponse;
import com.gestaocasas.application.mapper.PagamentoMapper;
import com.gestaocasas.domain.entity.LancamentoConta;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.TipoCanal;
import com.gestaocasas.domain.repository.LancamentoContaRepository;
import com.gestaocasas.domain.repository.PagamentoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * RF-43 e RN-09: a conta do hóspede. A diária de reserva de OTA é crédito a receber do canal (repasse) e não
 * entra no saldo do hóspede.
 */
@Component
public class ContaReserva {

    private final LancamentoContaRepository lancamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PagamentoMapper mapper;

    public ContaReserva(LancamentoContaRepository lancamentoRepository, PagamentoRepository pagamentoRepository,
                        PagamentoMapper mapper) {
        this.lancamentoRepository = lancamentoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.mapper = mapper;
    }

    public ContaResponse calcular(Reserva reserva) {
        List<LancamentoConta> lancamentos = lancamentoRepository.findDaReserva(reserva.getId());
        List<Pagamento> pagamentos = pagamentoRepository.findDaReserva(reserva.getId());
        BigDecimal diarias = reserva.getValorTotal();
        BigDecimal repasse = repasseCanal(reserva);
        BigDecimal consumos = consumos(lancamentos);
        BigDecimal pago = pagos(pagamentos);
        return new ContaResponse(reserva.getId(), diarias, repasse, consumos, pago,
                saldo(diarias, repasse, consumos, pago),
                lancamentos.stream().map(mapper::toResponse).toList(),
                pagamentos.stream().map(mapper::toResponse).toList());
    }

    public BigDecimal saldo(Reserva reserva) {
        return calcular(reserva).saldo();
    }

    public static BigDecimal repasseCanal(Reserva reserva) {
        return reserva.getCanal().getTipo() == TipoCanal.OTA ? reserva.getValorTotal() : BigDecimal.ZERO.setScale(2);
    }

    /** Lançamentos não estornados. */
    public static BigDecimal consumos(Collection<LancamentoConta> lancamentos) {
        return lancamentos.stream().filter(l -> !l.isEstornado()).map(LancamentoConta::getTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static BigDecimal pagos(Collection<Pagamento> pagamentos) {
        return pagamentos.stream().map(Pagamento::getValorBruto).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static BigDecimal saldo(BigDecimal diarias, BigDecimal repasse, BigDecimal consumos, BigDecimal pagos) {
        return diarias.subtract(repasse).add(consumos).subtract(pagos);
    }
}
