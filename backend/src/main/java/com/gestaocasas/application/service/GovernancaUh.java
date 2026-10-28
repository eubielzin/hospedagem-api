package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.BloqueioRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Status de governança das UHs. O imóvel guarda só o que a governança define (LIVRE, SUJA, MANUTENCAO);
 * OCUPADA vem das reservas hospedadas e BLOQUEADA dos bloqueios vigentes hoje. Com o imóvel completo
 * ocupado ou bloqueado, todas as casas também ficam.
 */
@Component
public class GovernancaUh {

    private final ImovelRepository imovelRepository;
    private final ReservaRepository reservaRepository;
    private final BloqueioRepository bloqueioRepository;
    private final Clock clock;

    public GovernancaUh(ImovelRepository imovelRepository, ReservaRepository reservaRepository,
                        BloqueioRepository bloqueioRepository, Clock clock) {
        this.imovelRepository = imovelRepository;
        this.reservaRepository = reservaRepository;
        this.bloqueioRepository = bloqueioRepository;
        this.clock = clock;
    }

    /** Todas as UHs: casas primeiro, depois o imóvel completo. */
    public List<UhResponse> listar() {
        Situacao situacao = situacao();
        return imovelRepository.findAllUhs().stream()
                .map(imovel -> toResponse(imovel, situacao))
                .toList();
    }

    public UhResponse toResponse(Imovel imovel) {
        return toResponse(imovel, situacao());
    }

    /** RN-07: com hóspede na casa, o status só muda no check-out. */
    public void definir(Imovel imovel, StatusGovernanca status) {
        if (!status.manual()) {
            throw new IllegalArgumentException("Status de governança calculado não pode ser gravado: " + status);
        }
        if (situacao().ocupada(imovel)) {
            throw new RegraDeNegocioException("RN-07", imovel.getNome() + " está ocupada: o status muda no check-out.");
        }
        imovel.setStatusGovernanca(status);
    }

    /** RN-07: depois do check-out (ou da troca de casa com o hóspede dentro), a UH espera a limpeza. */
    public void marcarSuja(Imovel uh) {
        for (Imovel imovel : daOcupacao(uh)) {
            if (imovel.getStatusGovernanca() == StatusGovernanca.LIVRE) {
                imovel.setStatusGovernanca(StatusGovernanca.SUJA);
            }
        }
    }

    /** RF-35: o hóspede só entra em UH liberada pela governança (nem suja nem em manutenção). */
    public void exigirLiberada(Imovel uh) {
        for (Imovel imovel : daOcupacao(uh)) {
            if (imovel.getStatusGovernanca() != StatusGovernanca.LIVRE) {
                throw new RegraDeNegocioException("RF-35", imovel.getNome()
                        + " ainda não foi liberada pela governança.");
            }
        }
    }

    /** A UH e, no imóvel completo, as casas ativas que ele reúne. */
    private List<Imovel> daOcupacao(Imovel uh) {
        List<Imovel> imoveis = new ArrayList<>();
        imoveis.add(uh);
        if (uh.getTipo() == TipoUh.IMOVEL_COMPLETO) {
            imovelRepository.findByTipo(TipoUh.CASA).stream()
                    .filter(casa -> casa.getStatus() == StatusImovel.ATIVO)
                    .forEach(imoveis::add);
        }
        return imoveis;
    }

    private UhResponse toResponse(Imovel imovel, Situacao situacao) {
        StatusGovernanca status = imovel.getStatusGovernanca();
        if (situacao.ocupada(imovel)) {
            status = StatusGovernanca.OCUPADA;
        } else if (status == StatusGovernanca.LIVRE && situacao.bloqueada(imovel)) {
            status = StatusGovernanca.BLOQUEADA;
        }
        return new UhResponse(imovel.getId(), imovel.getNome(), imovel.getTipo(), imovel.getCategoriaId(),
                imovel.getCapacidadeAdultos(), imovel.getCapacidadeCriancas(), status,
                imovel.getStatus() == StatusImovel.ATIVO);
    }

    private Situacao situacao() {
        Set<UUID> hospedados = reservaRepository.findImoveisHospedados();
        Set<UUID> bloqueados = bloqueioRepository.findImoveisBloqueadosEm(LocalDate.now(clock));
        Optional<UUID> completo = hospedados.isEmpty() && bloqueados.isEmpty() ? Optional.empty()
                : imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO).map(Imovel::getId);
        return new Situacao(hospedados, bloqueados, completo.orElse(null));
    }

    private record Situacao(Set<UUID> hospedados, Set<UUID> bloqueados, UUID completo) {

        /** Imóvel ainda não gravado (sem id) não tem reserva. */
        boolean ocupada(Imovel imovel) {
            return contem(hospedados, imovel);
        }

        boolean bloqueada(Imovel imovel) {
            return contem(bloqueados, imovel);
        }

        private boolean contem(Set<UUID> ids, Imovel imovel) {
            return imovel.getId() != null && (ids.contains(imovel.getId())
                    || (completo != null && ids.contains(completo) && imovel.getTipo() == TipoUh.CASA));
        }
    }
}
