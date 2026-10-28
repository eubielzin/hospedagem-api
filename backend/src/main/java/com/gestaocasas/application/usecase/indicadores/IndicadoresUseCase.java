package com.gestaocasas.application.usecase.indicadores;

import com.gestaocasas.application.dto.IndicadoresResponse;
import com.gestaocasas.application.dto.PrevisaoResponse;
import com.gestaocasas.application.usecase.indicadores.CalculadoraIndicadores.Consumo;
import com.gestaocasas.application.usecase.indicadores.CalculadoraIndicadores.Dados;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.LancamentoConta;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.repository.CanalRepository;
import com.gestaocasas.domain.repository.DespesaRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.LancamentoContaRepository;
import com.gestaocasas.domain.repository.PagamentoRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import com.gestaocasas.domain.repository.VendaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicadores do período (módulo 8) e previsão (on the books). Carrega só o intervalo necessário (o período pedido
 * e o anterior, de mesmo tamanho) e calcula em memória; com o volume de uma pousada fica bem abaixo dos 10 s do RNF.
 */
@Component
public class IndicadoresUseCase {

    /** Períodos maiores pesariam demais na memória e na tela. */
    static final int MAXIMO_DIAS = 731;

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final CanalRepository canalRepository;
    private final LancamentoContaRepository lancamentoRepository;
    private final VendaRepository vendaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final DespesaRepository despesaRepository;
    private final Clock clock;

    public IndicadoresUseCase(ReservaRepository reservaRepository, ImovelRepository imovelRepository,
                              CanalRepository canalRepository, LancamentoContaRepository lancamentoRepository,
                              VendaRepository vendaRepository, PagamentoRepository pagamentoRepository,
                              DespesaRepository despesaRepository, Clock clock) {
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.canalRepository = canalRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.vendaRepository = vendaRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.despesaRepository = despesaRepository;
        this.clock = clock;
    }

    /** inicio e fim inclusive. */
    @Transactional(readOnly = true)
    public IndicadoresResponse indicadores(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new CampoInvalidoException("fim", "O fim do período deve ser depois do início.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new CampoInvalidoException("fim", "O período pode ter no máximo " + MAXIMO_DIAS + " dias.");
        }
        return calculadora(inicio.minusDays(dias), fim).indicadores(inicio, fim);
    }

    @Transactional(readOnly = true)
    public PrevisaoResponse previsao(LocalDate inicio, int dias) {
        if (dias < 1 || dias > 366) {
            throw new CampoInvalidoException("dias", "Informe de 1 a 366 dias.");
        }
        LocalDate hoje = LocalDate.now(clock);
        ZoneId zona = clock.getZone();
        int vendidas7 = (int) reservaRepository.countAtivasCriadasDesde(hoje.minusDays(7).atStartOfDay(zona).toInstant());
        int vendidas30 = (int) reservaRepository.countAtivasCriadasDesde(hoje.minusDays(30).atStartOfDay(zona).toInstant());
        return calculadora(inicio, inicio.plusDays(dias - 1)).previsao(inicio, dias, vendidas7, vendidas30);
    }

    private CalculadoraIndicadores calculadora(LocalDate desde, LocalDate ate) {
        ZoneId zona = clock.getZone();
        List<Imovel> casas = imovelRepository.findByTipo(TipoUh.CASA).stream()
                .filter(casa -> casa.getStatus() == StatusImovel.ATIVO)
                .sorted(Comparator.comparing(Imovel::getNome))
                .toList();
        List<Reserva> reservas = reservaRepository.findParaIndicadores(desde, ate);
        Map<UUID, LocalDate> primeiraEstadia = new HashMap<>();
        for (Object[] linha : reservaRepository.findPrimeiraEstadiaPorTitular()) {
            primeiraEstadia.put((UUID) linha[0], linha[1] instanceof java.sql.Date data ? data.toLocalDate()
                    : (LocalDate) linha[1]);
        }

        List<Consumo> consumos = new ArrayList<>();
        for (LancamentoConta l : lancamentoRepository.findNaoEstornadosEntre(desde, ate)) {
            consumos.add(new Consumo(l.getData(), l.getReserva().getId(), l.getDescricao(),
                    l.getProduto() == null ? null : l.getProduto().getCategoria(), l.getQuantidade(),
                    l.getTotal().doubleValue()));
        }
        // Venda avulsa: conta o que não foi devolvido.
        for (Venda venda : vendaRepository.findConcluidasEntre(desde.atStartOfDay(zona).toInstant(),
                ate.plusDays(1).atStartOfDay(zona).toInstant())) {
            LocalDate data = LocalDate.ofInstant(venda.getCriadoEm(), zona);
            for (ItemVenda item : venda.getItens()) {
                int quantidade = item.quantidadeNaoDevolvida();
                if (quantidade > 0) {
                    consumos.add(new Consumo(data, null, item.getProdutoNome(), item.getProduto().getCategoria(),
                            quantidade, item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)).doubleValue()));
                }
            }
        }
        return new CalculadoraIndicadores(new Dados(casas, canalRepository.findAll(), reservas, primeiraEstadia,
                consumos, pagamentoRepository.findNoPeriodo(desde, ate), despesaRepository.findPorVencimento(desde, ate),
                r -> LocalDate.ofInstant(r.getCriadoEm(), zona)));
    }
}
