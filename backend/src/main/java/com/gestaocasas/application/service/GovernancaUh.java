package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.UhResponse;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Status de governança das UHs. O imóvel guarda só o que a governança define (LIVRE, SUJA, MANUTENCAO);
 * OCUPADA vem das reservas hospedadas e, com o imóvel completo hospedado, vale para todas as casas.
 */
@Component
public class GovernancaUh {

    private final ImovelRepository imovelRepository;
    private final ReservaRepository reservaRepository;

    public GovernancaUh(ImovelRepository imovelRepository, ReservaRepository reservaRepository) {
        this.imovelRepository = imovelRepository;
        this.reservaRepository = reservaRepository;
    }

    /** Todas as UHs: casas primeiro, depois o imóvel completo. */
    public List<UhResponse> listar() {
        Ocupacao ocupacao = ocupacao();
        return imovelRepository.findAllUhs().stream()
                .map(imovel -> toResponse(imovel, ocupacao))
                .toList();
    }

    public UhResponse toResponse(Imovel imovel) {
        return toResponse(imovel, ocupacao());
    }

    /** RN-07: com hóspede na casa, o status só muda no check-out. */
    public void definir(Imovel imovel, StatusGovernanca status) {
        if (!status.manual()) {
            throw new IllegalArgumentException("Status de governança calculado não pode ser gravado: " + status);
        }
        if (ocupacao().ocupada(imovel)) {
            throw new RegraDeNegocioException("RN-07", imovel.getNome() + " está ocupada: o status muda no check-out.");
        }
        imovel.setStatusGovernanca(status);
    }

    private UhResponse toResponse(Imovel imovel, Ocupacao ocupacao) {
        StatusGovernanca status = ocupacao.ocupada(imovel) ? StatusGovernanca.OCUPADA : imovel.getStatusGovernanca();
        return new UhResponse(imovel.getId(), imovel.getNome(), imovel.getTipo(), imovel.getCategoriaId(),
                imovel.getCapacidadeAdultos(), imovel.getCapacidadeCriancas(), status,
                imovel.getStatus() == StatusImovel.ATIVO);
    }

    private Ocupacao ocupacao() {
        Set<UUID> hospedados = reservaRepository.findImoveisHospedados();
        boolean completoHospedado = !hospedados.isEmpty() && imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)
                .map(completo -> hospedados.contains(completo.getId()))
                .orElse(false);
        return new Ocupacao(hospedados, completoHospedado);
    }

    private record Ocupacao(Set<UUID> hospedados, boolean completoHospedado) {

        /** Imóvel ainda não gravado (sem id) não tem reserva. */
        boolean ocupada(Imovel imovel) {
            return imovel.getId() != null && (hospedados.contains(imovel.getId())
                    || (completoHospedado && imovel.getTipo() == TipoUh.CASA));
        }
    }
}
