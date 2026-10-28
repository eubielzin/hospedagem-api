package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.application.service.CotadorReserva;
import com.gestaocasas.application.service.DisponibilidadeUh;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.RegimePensao;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.CanalNotFoundException;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.CanalRepository;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Valida os dados de uma reserva nova ou editada e os copia para a entidade, na ordem: UH ativa, titular,
 * canal, cotação, capacidade e disponibilidade. Deve rodar dentro da transação do caso de uso: as UHs ficam
 * travadas até o commit (RNF-09).
 */
@Component
public class PreenchedorReserva {

    private final HospedeRepository hospedeRepository;
    private final CanalRepository canalRepository;
    private final DisponibilidadeUh disponibilidade;
    private final CotadorReserva cotador;

    public PreenchedorReserva(HospedeRepository hospedeRepository, CanalRepository canalRepository,
                              DisponibilidadeUh disponibilidade, CotadorReserva cotador) {
        this.hospedeRepository = hospedeRepository;
        this.canalRepository = canalRepository;
        this.disponibilidade = disponibilidade;
        this.cotador = cotador;
    }

    /** @param reserva nova (sem id) ou a existente, na edição */
    void preencher(ReservaRequest request, Reserva reserva) {
        if (!request.checkout().isAfter(request.checkin())) {
            throw new CampoInvalidoException("checkout", "A saída deve ser depois da entrada.");
        }
        int criancas = request.criancas() == null ? 0 : request.criancas();

        Imovel uh = disponibilidade.travar(request.uhId());
        disponibilidade.exigirAtiva(uh);
        // Na edição, a reserva que já estava nesta UH continua editável se ela entrar em manutenção depois.
        if (!mesmoId(reserva.getImovel() == null ? null : reserva.getImovel().getId(), uh.getId())) {
            disponibilidade.exigirForaDeManutencao(uh);
        }

        Hospede titular = hospedeRepository.findById(request.titularId())
                .orElseThrow(() -> new HospedeNotFoundException(request.titularId()));
        if (!titular.isAtivo() && !mesmoId(reserva.getTitular() == null ? null : reserva.getTitular().getId(),
                titular.getId())) {
            throw new CampoInvalidoException("titularId", "O hóspede titular está inativo.");
        }

        Canal canal = canalRepository.findById(request.canalId())
                .orElseThrow(() -> new CanalNotFoundException(request.canalId()));
        // Na edição, manter um canal que foi inativado depois é permitido.
        if (!canal.isAtivo() && !mesmoId(reserva.getCanal() == null ? null : reserva.getCanal().getId(),
                canal.getId())) {
            throw new RegraDeNegocioException("O canal '" + canal.getNome() + "' está inativo");
        }

        CotacaoResponse cotacao = cotador.cotar(uh, request.checkin(), request.checkout());
        exigirCapacidade(uh, request.adultos(), criancas);
        disponibilidade.verificar(uh, request.checkin(), request.checkout(), reserva.getId());

        reserva.setImovel(uh);
        reserva.setTitular(titular);
        reserva.setCanal(canal);
        reserva.setCheckin(request.checkin());
        reserva.setCheckout(request.checkout());
        reserva.setAdultos(request.adultos());
        reserva.setCriancas(criancas);
        reserva.setValorTotal(cotacao.valorTotal());
        reserva.setRegimePensao(request.regimePensao() == null ? RegimePensao.SEM_REFEICOES : request.regimePensao());
        reserva.setCodigoExterno(texto(request.codigoExterno()));
        reserva.setObservacoes(texto(request.observacoes()));
    }

    /** RN-13. */
    static void exigirCapacidade(Imovel uh, int adultos, int criancas) {
        if (adultos > uh.getCapacidadeAdultos() || criancas > uh.getCapacidadeCriancas()) {
            throw new RegraDeNegocioException("RN-13", uh.getNome() + " comporta " + uh.getCapacidadeAdultos()
                    + " adultos e " + uh.getCapacidadeCriancas() + " crianças.");
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static boolean mesmoId(UUID atual, UUID novo) {
        return atual != null && atual.equals(novo);
    }
}
