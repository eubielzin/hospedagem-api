package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Tarifa;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.TarifaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RF-31, RF-32 e RN-11: o valor da reserva vem das tarifas da categoria da UH, iguais para todos os canais.
 * Em cada noite vale, entre as tarifas que a cobrem, a de período mais curto (a mais específica).
 */
@Component
public class CotadorReserva {

    // Mais curta primeiro; no empate, a que começou por último, e então pelo nome, para o resultado não variar.
    private static final Comparator<Tarifa> MAIS_ESPECIFICA = Comparator
            .comparingLong((Tarifa t) -> ChronoUnit.DAYS.between(t.getInicio(), t.getFim()))
            .thenComparing(Tarifa::getInicio, Comparator.reverseOrder())
            .thenComparing(Tarifa::getNome);

    private final TarifaRepository tarifaRepository;

    public CotadorReserva(TarifaRepository tarifaRepository) {
        this.tarifaRepository = tarifaRepository;
    }

    public CotacaoResponse cotar(Imovel uh, LocalDate checkin, LocalDate checkout) {
        long noites = ChronoUnit.DAYS.between(checkin, checkout);
        if (noites < 1) {
            throw new CampoInvalidoException("checkout", "A saída deve ser depois da entrada.");
        }
        // O dia do checkout entra na busca: a tarifa dele diz se a saída está fechada.
        List<Tarifa> tarifas = uh.getCategoria() == null ? List.of()
                : tarifaRepository.findDaCategoriaNoPeriodo(uh.getCategoria().getId(), checkin, checkout);

        List<CotacaoResponse.Diaria> diarias = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int estadiaMinima = 1;
        Tarifa primeira = null;
        for (LocalDate noite = checkin; noite.isBefore(checkout); noite = noite.plusDays(1)) {
            LocalDate data = noite;
            Tarifa tarifa = vigente(tarifas, data).orElseThrow(() -> new RegraDeNegocioException("RF-31",
                    "Não há tarifa cadastrada para " + uh.getNome() + " em " + Formato.data(data) + "."));
            if (tarifa.isVendaFechada()) {
                throw new RegraDeNegocioException("RF-32",
                        "Venda fechada em " + Formato.data(data) + " (" + tarifa.getNome() + ").");
            }
            if (primeira == null) {
                primeira = tarifa;
            }
            estadiaMinima = Math.max(estadiaMinima, tarifa.getEstadiaMinima());
            diarias.add(new CotacaoResponse.Diaria(data, tarifa.getValorDiaria(), tarifa.getNome()));
            total = total.add(tarifa.getValorDiaria());
        }
        if (primeira.isFechadoChegada()) {
            throw new RegraDeNegocioException("RF-32", "Período fechado para chegada (" + primeira.getNome() + ").");
        }
        Optional<Tarifa> saida = vigente(tarifas, checkout);
        if (saida.isPresent() && saida.get().isFechadoSaida()) {
            throw new RegraDeNegocioException("RF-32", "Período fechado para saída (" + saida.get().getNome() + ").");
        }
        if (noites < estadiaMinima) {
            throw new RegraDeNegocioException("RF-32", "Estadia mínima de " + estadiaMinima + " noites no período.");
        }
        return new CotacaoResponse((int) noites, total, diarias);
    }

    private static Optional<Tarifa> vigente(List<Tarifa> tarifas, LocalDate data) {
        return tarifas.stream()
                .filter(t -> !t.getInicio().isAfter(data) && !t.getFim().isBefore(data))
                .min(MAIS_ESPECIFICA);
    }
}
