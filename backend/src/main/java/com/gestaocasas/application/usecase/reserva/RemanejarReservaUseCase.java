package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.application.dto.RemanejamentoRequest;
import com.gestaocasas.application.dto.ReservaResponse;
import com.gestaocasas.application.mapper.ReservaMapper;
import com.gestaocasas.application.service.CotadorReserva;
import com.gestaocasas.application.service.DisponibilidadeUh;
import com.gestaocasas.application.service.Formato;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF-06: troca de datas e/ou de UH, com o valor recotado. Com o hóspede na casa, a entrada não muda; se ele
 * troca de casa, a antiga fica suja (RN-07) e a nova precisa estar liberada (RF-35).
 */
@Component
public class RemanejarReservaUseCase {

    private final ReservaRepository repository;
    private final DisponibilidadeUh disponibilidade;
    private final CotadorReserva cotador;
    private final GovernancaUh governanca;
    private final AcoesReserva acoes;
    private final ReservaMapper mapper;

    public RemanejarReservaUseCase(ReservaRepository repository, DisponibilidadeUh disponibilidade,
                                   CotadorReserva cotador, GovernancaUh governanca, AcoesReserva acoes,
                                   ReservaMapper mapper) {
        this.repository = repository;
        this.disponibilidade = disponibilidade;
        this.cotador = cotador;
        this.governanca = governanca;
        this.acoes = acoes;
        this.mapper = mapper;
    }

    @Transactional
    public ReservaResponse execute(UUID id, RemanejamentoRequest request) {
        Reserva reserva = acoes.buscar(id);
        AcoesReserva.exigirStatus(reserva, "remanejar", StatusReserva.RESERVADO, StatusReserva.HOSPEDADO);
        boolean hospedado = reserva.getStatus() == StatusReserva.HOSPEDADO;
        if (hospedado && !request.checkin().equals(reserva.getCheckin())) {
            throw new RegraDeNegocioException("RN-03", "Com o hóspede na casa, só a saída e a UH podem mudar.");
        }
        if (!request.checkout().isAfter(request.checkin())) {
            throw new CampoInvalidoException("checkout", "A saída deve ser depois da entrada.");
        }
        if (hospedado && request.checkout().isBefore(acoes.hoje())) {
            throw new CampoInvalidoException("checkout", "Com o hóspede na casa, a saída não pode ficar no passado.");
        }

        Imovel antiga = reserva.getImovel();
        Imovel nova = disponibilidade.travar(request.uhId());
        boolean trocaDeUh = !nova.getId().equals(antiga.getId());
        if (trocaDeUh) {
            disponibilidade.exigirAtiva(nova);
            disponibilidade.exigirForaDeManutencao(nova);
            if (hospedado) {
                governanca.exigirLiberada(nova);
            }
        }
        CotacaoResponse cotacao = cotador.cotar(nova, request.checkin(), request.checkout());
        PreenchedorReserva.exigirCapacidade(nova, reserva.getAdultos(), reserva.getCriancas());
        disponibilidade.verificar(nova, request.checkin(), request.checkout(), reserva.getId());

        String antes = resumo(reserva);
        if (hospedado && trocaDeUh) {
            governanca.marcarSuja(antiga);
        }
        reserva.setImovel(nova);
        reserva.setCheckin(request.checkin());
        reserva.setCheckout(request.checkout());
        reserva.setValorTotal(cotacao.valorTotal());
        acoes.registrar(reserva, "Remanejada de (" + antes + ") para (" + resumo(reserva) + ")");
        return mapper.toResponse(repository.saveAndFlush(reserva));
    }

    /** "Casa Flor, 10/11/2026 a 13/11/2026, R$ 1.140,00": como a reserva aparece no histórico. */
    static String resumo(Reserva reserva) {
        return reserva.getImovel().getNome() + ", " + Formato.data(reserva.getCheckin()) + " a "
                + Formato.data(reserva.getCheckout()) + ", " + Formato.dinheiro(reserva.getValorTotal());
    }
}
