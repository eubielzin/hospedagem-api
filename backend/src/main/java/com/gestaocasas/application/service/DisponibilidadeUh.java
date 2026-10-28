package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.BloqueioRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * RN-01, RN-02 e RNF-09. Uma UH não pode ter reservas ativas nem bloqueios sobrepostos; o imóvel completo
 * conflita com qualquer casa e cada casa com o imóvel completo. Toda gravação que ocupa uma UH chama
 * {@link #travar} antes de {@link #verificar}, na mesma transação.
 */
@Component
public class DisponibilidadeUh {

    private static final UUID NENHUMA = new UUID(0, 0);

    private final ImovelRepository imovelRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueioRepository bloqueioRepository;

    public DisponibilidadeUh(ImovelRepository imovelRepository, ReservaRepository reservaRepository,
                             BloqueioRepository bloqueioRepository) {
        this.imovelRepository = imovelRepository;
        this.reservaRepository = reservaRepository;
        this.bloqueioRepository = bloqueioRepository;
    }

    /**
     * Bloqueia até o commit a linha da UH e a do imóvel completo, sempre em ordem de id: duas gravações em
     * casas diferentes não se esperam à toa, e uma na casa e outra no completo se enfileiram sem deadlock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Imovel travar(UUID uhId) {
        TreeSet<UUID> ids = new TreeSet<>();
        ids.add(uhId);
        imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO).ifPresent(completo -> ids.add(completo.getId()));
        Imovel uh = null;
        for (UUID id : ids) {
            Imovel travado = imovelRepository.findByIdForUpdate(id).orElse(null);
            if (id.equals(uhId)) {
                uh = travado;
            }
        }
        if (uh == null) {
            throw new ImovelNotFoundException(uhId);
        }
        return uh;
    }

    /** RN-15: UH inativa não recebe reserva nem bloqueio. */
    public void exigirAtiva(Imovel uh) {
        if (uh.getStatus() != StatusImovel.ATIVO) {
            throw new RegraDeNegocioException("RN-15", uh.getNome() + " está inativa.");
        }
    }

    /**
     * RF-35: UH em manutenção não recebe reserva nova. Vale nos dois sentidos do RN-02: o imóvel completo
     * inclui todas as casas, e cada casa faz parte dele.
     */
    public void exigirForaDeManutencao(Imovel uh) {
        if (uh.getStatusGovernanca() == StatusGovernanca.MANUTENCAO) {
            throw new RegraDeNegocioException("RF-35", "A UH '" + uh.getNome() + "' está em manutenção.");
        }
        if (uh.getTipo() == TipoUh.IMOVEL_COMPLETO) {
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

    /**
     * @param fimExclusivo checkout da reserva, ou o dia seguinte ao fim do bloqueio
     * @param ignorarReserva a própria reserva, na edição e no remanejamento; nulo na criação
     */
    public void verificar(Imovel uh, LocalDate inicio, LocalDate fimExclusivo, UUID ignorarReserva) {
        List<UUID> relacionadas = relacionadas(uh);
        reservaRepository.findConflito(relacionadas, inicio, fimExclusivo,
                ignorarReserva == null ? NENHUMA : ignorarReserva).ifPresent(outra -> {
            throw new RegraDeNegocioException(codigo(uh, outra.getImovel()), outra.getImovel().getNome()
                    + " já tem a reserva " + outra.getCodigo() + " de " + Formato.data(outra.getCheckin())
                    + " a " + Formato.data(outra.getCheckout()) + ".");
        });
        bloqueioRepository.findConflito(relacionadas, inicio, fimExclusivo).ifPresent(bloqueio -> {
            throw new RegraDeNegocioException(codigo(uh, bloqueio.getImovel()), bloqueio.getImovel().getNome()
                    + " está bloqueada de " + Formato.data(bloqueio.getInicio()) + " a "
                    + Formato.data(bloqueio.getFim()) + " (" + bloqueio.getMotivo() + ").");
        });
    }

    /** A própria UH e as que conflitam com ela: o completo conflita com todas; a casa, com o completo. */
    private List<UUID> relacionadas(Imovel uh) {
        if (uh.getTipo() == TipoUh.IMOVEL_COMPLETO) {
            return imovelRepository.findAllIds();
        }
        TreeSet<UUID> ids = new TreeSet<>();
        ids.add(uh.getId());
        imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO).ifPresent(completo -> ids.add(completo.getId()));
        return List.copyOf(ids);
    }

    private static String codigo(Imovel uh, Imovel outra) {
        return uh.getId().equals(outra.getId()) ? "RN-01" : "RN-02";
    }
}
