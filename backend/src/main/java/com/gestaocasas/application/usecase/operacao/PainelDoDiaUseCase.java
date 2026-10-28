package com.gestaocasas.application.usecase.operacao;

import com.gestaocasas.application.dto.PessoasDoDiaResponse;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.dto.ResumoDiaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** RF-55 (painel do dia) e RF-51 a RF-53 (pessoas do dia), calculados a partir das reservas. */
@Component
public class PainelDoDiaUseCase {

    private static final Comparator<Reserva> POR_UH = Comparator.comparing((Reserva r) -> r.getImovel().getNome())
            .thenComparing(Reserva::getCodigo);

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final ReservaMapper mapper;

    public PainelDoDiaUseCase(ReservaRepository reservaRepository, ImovelRepository imovelRepository,
                              ReservaMapper mapper) {
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ResumoDiaResponse resumo(LocalDate data) {
        LocalDate amanha = data.plusDays(1);
        // Ativas que tocam data ou amanhã: na casa, chegando ou saindo.
        List<Reserva> ativas = reservaRepository.findAtivasEntre(data, amanha);
        List<Reserva> naData = ativas.stream()
                .filter(r -> !r.getCheckin().isAfter(data) && r.getCheckout().isAfter(data)).toList();
        int totalUhs = (int) imovelRepository.findByTipo(TipoUh.CASA).stream()
                .filter(casa -> casa.getStatus() == StatusImovel.ATIVO).count();
        boolean completoReservado = naData.stream().anyMatch(r -> r.getImovel().getTipo() == TipoUh.IMOVEL_COMPLETO);
        int ocupadas = completoReservado ? totalUhs
                : (int) naData.stream().map(Reserva::getImovel).map(Imovel::getId).distinct().count();
        List<Reserva> hospedadas = naData.stream().filter(r -> r.getStatus() == StatusReserva.HOSPEDADO).toList();

        BigDecimal diarias = naData.stream()
                .map(r -> r.getValorTotal().divide(BigDecimal.valueOf(r.getNoites()), 10, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumoDiaResponse(data, totalUhs, ocupadas, razao(BigDecimal.valueOf(ocupadas), totalUhs),
                hospedadas.stream().mapToInt(Reserva::getAdultos).sum(),
                hospedadas.stream().mapToInt(Reserva::getCriancas).sum(),
                diarias.setScale(2, RoundingMode.HALF_UP),
                dinheiro(diarias, naData.size()), dinheiro(diarias, totalUhs),
                lista(ativas, r -> r.getCheckin().equals(data)),
                lista(ativas, r -> r.getCheckout().equals(data)),
                lista(ativas, r -> r.getCheckin().equals(amanha)),
                lista(ativas, r -> r.getCheckout().equals(amanha)));
    }

    /**
     * RN-12: só quem está hospedado e dorme na casa na noite da data, por UH. Quem sai no dia não entra (afeta o
     * café da manhã do dia da saída; a validar com a pousada).
     */
    @Transactional(readOnly = true)
    public PessoasDoDiaResponse pessoas(LocalDate data) {
        List<PessoasDoDiaResponse.Linha> linhas = reservaRepository.findHospedadasNaNoite(data).stream()
                .sorted(POR_UH)
                .map(r -> new PessoasDoDiaResponse.Linha(r.getId(), r.getCodigo(), r.getImovel().getNome(),
                        r.getTitular().getNome(), r.getAdultos(), r.getCriancas(), r.getRegimePensao().rotulo(),
                        r.getTitular().getObservacoesAlimentares()))
                .toList();
        return new PessoasDoDiaResponse(data, linhas,
                linhas.stream().mapToInt(PessoasDoDiaResponse.Linha::adultos).sum(),
                linhas.stream().mapToInt(PessoasDoDiaResponse.Linha::criancas).sum());
    }

    private List<ReservaResponse> lista(List<Reserva> reservas, Predicate<Reserva> filtro) {
        return reservas.stream().filter(filtro).sorted(POR_UH).map(mapper::toResumo).toList();
    }

    private static BigDecimal razao(BigDecimal valor, int total) {
        return total == 0 ? BigDecimal.ZERO : valor.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinheiro(BigDecimal valor, int total) {
        return total == 0 ? BigDecimal.ZERO.setScale(2)
                : valor.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
