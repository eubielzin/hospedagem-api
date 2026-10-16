package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.application.dto.ImovelRequest;
import com.gestaocasas.application.mapper.ImovelMapper;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.CategoriaUhNotFoundException;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;

/**
 * Copia o cadastro para o imóvel, novo ou existente. Os campos da UH que não vierem na edição ficam como
 * estão: o front que ainda não os conhece não pode apagá-los.
 */
@Component
public class PreenchedorImovel {

    private final ImovelMapper mapper;
    private final ImovelRepository imovelRepository;
    private final CategoriaUhRepository categoriaRepository;
    private final ReservaRepository reservaRepository;
    private final GovernancaUh governanca;

    public PreenchedorImovel(ImovelMapper mapper, ImovelRepository imovelRepository,
                             CategoriaUhRepository categoriaRepository, ReservaRepository reservaRepository,
                             GovernancaUh governanca) {
        this.mapper = mapper;
        this.imovelRepository = imovelRepository;
        this.categoriaRepository = categoriaRepository;
        this.reservaRepository = reservaRepository;
        this.governanca = governanca;
    }

    void preencher(ImovelRequest request, Imovel imovel) {
        boolean novo = imovel.getId() == null;
        // Antes de copiar o cadastro: as mensagens citam o nome atual do imóvel.
        definirTipo(request, imovel, novo);
        mapper.updateEntity(request, imovel);
        if (imovel.getTipo() == TipoUh.IMOVEL_COMPLETO && (request.logradouro() == null || request.logradouro().isBlank())) {
            copiarEnderecoDaPousada(imovel);
        }
        if (request.categoriaId() != null) {
            imovel.setCategoria(categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new CategoriaUhNotFoundException(request.categoriaId())));
        }
        definirCapacidade(request, imovel, novo);
        if (novo) {
            imovel.setStatus(StatusImovel.ATIVO);
            imovel.setStatusGovernanca(StatusGovernanca.LIVRE);
        }
        if (request.status() == StatusImovel.MANUTENCAO) {
            // Forma antiga: a manutenção agora é da governança.
            imovel.setStatus(StatusImovel.ATIVO);
            governanca.definir(imovel, StatusGovernanca.MANUTENCAO);
        } else if (request.status() != null) {
            imovel.setStatus(request.status());
        }
    }

    /** RN-02: só existe um imóvel completo, e uma UH com reservas ativas não muda de tipo. */
    private void definirTipo(ImovelRequest request, Imovel imovel, boolean novo) {
        TipoUh tipo = request.tipo() != null ? request.tipo() : novo ? TipoUh.CASA : imovel.getTipo();
        if (tipo == TipoUh.IMOVEL_COMPLETO) {
            imovelRepository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)
                    .filter(outro -> !outro.getId().equals(imovel.getId()))
                    .ifPresent(outro -> {
                        throw new ConflitoException("Já existe um imóvel completo: '" + outro.getNome() + "'");
                    });
        }
        if (!novo && tipo != imovel.getTipo() && reservaRepository.existeAtivaNoImovel(imovel.getId())) {
            throw new RegraDeNegocioException("Não é possível mudar o tipo de '" + imovel.getNome()
                    + "': há reservas ativas nele");
        }
        imovel.setTipo(tipo);
    }

    /** O imóvel completo fica onde ficam as casas: recebe o endereço da primeira cadastrada. */
    private void copiarEnderecoDaPousada(Imovel imovel) {
        Imovel casa = imovelRepository.findFirstByTipoOrderByCriadoEmAsc(TipoUh.CASA)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Informe o endereço do imóvel completo: ainda não há casa cadastrada para copiar o da pousada"));
        imovel.setLogradouro(casa.getLogradouro());
        imovel.setNumero(casa.getNumero());
        imovel.setComplemento(null);
        imovel.setBairro(casa.getBairro());
        imovel.setCidade(casa.getCidade());
        imovel.setUf(casa.getUf());
        imovel.setCep(casa.getCep());
    }

    /**
     * Com capacidadeAdultos, vale a capacidade separada (RN-13). Na forma antiga, só com o total, a
     * capacidade de crianças atual é mantida enquanto couber nele, e o resto fica para adultos.
     */
    private static void definirCapacidade(ImovelRequest request, Imovel imovel, boolean novo) {
        if (request.capacidadeAdultos() != null) {
            imovel.setCapacidadeAdultos(request.capacidadeAdultos());
            if (request.capacidadeCriancas() != null) {
                imovel.setCapacidadeCriancas(request.capacidadeCriancas());
            } else if (novo) {
                imovel.setCapacidadeCriancas(0);
            }
            return;
        }
        int total = request.capacidadeMaxima();
        if (!novo && total == imovel.getCapacidadeMaxima()) {
            return;
        }
        int criancas = novo ? 0 : Math.min(imovel.getCapacidadeCriancas(), total - 1);
        imovel.setCapacidadeCriancas(criancas);
        imovel.setCapacidadeAdultos(total - criancas);
    }
}
