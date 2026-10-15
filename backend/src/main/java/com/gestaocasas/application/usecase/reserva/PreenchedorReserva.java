package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.ReservaRequest;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusImovel;
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
            throw new RegraDeNegocioException("A UH '" + imovel.getNome() + "' não está disponível para reservas ("
                    + imovel.getStatus() + ")");
        }
        if (request.adultos() + criancas > imovel.getCapacidadeMaxima()) {
            throw new RegraDeNegocioException("A UH '" + imovel.getNome() + "' comporta no máximo "
                    + imovel.getCapacidadeMaxima() + " pessoas");
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

    private static boolean mesmoId(UUID atual, UUID novo) {
        return atual != null && atual.equals(novo);
    }
}
