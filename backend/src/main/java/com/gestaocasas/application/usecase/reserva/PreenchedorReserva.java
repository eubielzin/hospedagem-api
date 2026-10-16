package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.CanalNotFoundException;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.HospedeNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.CanalRepository;
import com.gestaocasas.domain.repository.HospedeRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Valida os dados de uma reserva nova ou editada e os copia para a entidade. Deve rodar dentro da
 * transação do caso de uso: a linha do imóvel fica bloqueada até o commit, então duas reservas
 * simultâneas para o mesmo imóvel são verificadas uma depois da outra.
 */
@Component
public class PreenchedorReserva {

    private static final UUID NENHUMA = new UUID(0, 0);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaRepository reservaRepository;
    private final ImovelRepository imovelRepository;
    private final HospedeRepository hospedeRepository;
    private final CanalRepository canalRepository;

    public PreenchedorReserva(ReservaRepository reservaRepository, ImovelRepository imovelRepository,
                              HospedeRepository hospedeRepository, CanalRepository canalRepository) {
        this.reservaRepository = reservaRepository;
        this.imovelRepository = imovelRepository;
        this.hospedeRepository = hospedeRepository;
        this.canalRepository = canalRepository;
    }

    /** @param reserva nova (sem id) ou a existente, na edição */
    void preencher(ReservaRequest request, Reserva reserva) {
        if (!request.checkout().isAfter(request.checkin())) {
            throw new RegraDeNegocioException("A data de saída deve ser depois da data de entrada");
        }
        int criancas = request.criancas() == null ? 0 : request.criancas();

        Imovel imovel = imovelRepository.findByIdForUpdate(request.uhId())
                .orElseThrow(() -> new ImovelNotFoundException(request.uhId()));
        if (imovel.getStatus() != StatusImovel.ATIVO) {
            throw new RegraDeNegocioException("RN-15", "A UH '" + imovel.getNome() + "' está inativa.");
        }
        // Na edição, a reserva que já estava nesta UH continua editável se ela entrar em manutenção depois.
        boolean mesmaUh = reserva.getImovel() != null && reserva.getImovel().getId().equals(imovel.getId());
        if (!mesmaUh) {
            exigirForaDeManutencao(imovel);
        }
        if (request.adultos() > imovel.getCapacidadeAdultos() || criancas > imovel.getCapacidadeCriancas()) {
            throw new RegraDeNegocioException("RN-13", "A UH '" + imovel.getNome() + "' comporta "
                    + imovel.getCapacidadeAdultos() + " adultos e " + imovel.getCapacidadeCriancas() + " crianças.");
        }

        Hospede titular = hospedeRepository.findById(request.titularId())
                .orElseThrow(() -> new HospedeNotFoundException(request.titularId()));
        if (!titular.isAtivo() && !mesmoId(reserva.getTitular() == null ? null : reserva.getTitular().getId(),
                titular.getId())) {
            throw new RegraDeNegocioException("O hóspede '" + titular.getNome() + "' está inativo");
        }

        Canal canal = canalRepository.findById(request.canalId())
                .orElseThrow(() -> new CanalNotFoundException(request.canalId()));
        // Na edição, manter um canal que foi inativado depois é permitido.
        if (!canal.isAtivo() && !mesmoId(reserva.getCanal() == null ? null : reserva.getCanal().getId(),
                canal.getId())) {
            throw new RegraDeNegocioException("O canal '" + canal.getNome() + "' está inativo");
        }

        UUID ignorar = reserva.getId() == null ? NENHUMA : reserva.getId();
        reservaRepository.findConflito(imovel.getId(), request.checkin(), request.checkout(), ignorar)
                .ifPresent(outra -> {
                    throw new ConflitoException("A UH '" + imovel.getNome() + "' já está reservada de "
                            + outra.getCheckin().format(DATA) + " a " + outra.getCheckout().format(DATA)
                            + " (" + outra.getCodigo() + ")");
                });

        reserva.setImovel(imovel);
        reserva.setTitular(titular);
        reserva.setCanal(canal);
        reserva.setCheckin(request.checkin());
        reserva.setCheckout(request.checkout());
        reserva.setAdultos(request.adultos());
        reserva.setCriancas(criancas);
        reserva.setValorTotal(request.valorTotal());
        reserva.setObservacoes(request.observacoes() == null || request.observacoes().isBlank()
                ? null : request.observacoes().trim());
    }

    /**
     * RF-35: UH em manutenção não recebe reserva nova. Vale nos dois sentidos do RN-02: o imóvel completo
     * inclui todas as casas, e cada casa faz parte dele.
     */
    private void exigirForaDeManutencao(Imovel imovel) {
        if (imovel.getStatusGovernanca() == StatusGovernanca.MANUTENCAO) {
            throw new RegraDeNegocioException("RF-35", "A UH '" + imovel.getNome() + "' está em manutenção.");
        }
        if (imovel.getTipo() == TipoUh.IMOVEL_COMPLETO) {
            imovelRepository.findFirstByTipoAndStatusAndStatusGovernancaOrderByNome(TipoUh.CASA, StatusImovel.ATIVO,
                    StatusGovernanca.MANUTENCAO).ifPresent(casa -> {
                throw new RegraDeNegocioException("RF-35", "O imóvel completo inclui a UH '" + casa.getNome()
                        + "', que está em manutenção.");
            });
        } else {
            imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)
                    .filter(completo -> completo.getStatusGovernanca() == StatusGovernanca.MANUTENCAO)
                    .ifPresent(completo -> {
                        throw new RegraDeNegocioException("RF-35", "O imóvel completo '" + completo.getNome()
                                + "', que inclui esta UH, está em manutenção.");
                    });
        }
    }

    private static boolean mesmoId(UUID atual, UUID novo) {
        return atual != null && atual.equals(novo);
    }
}
