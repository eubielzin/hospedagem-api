package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateImovelUseCaseTest {

    @Mock
    private ImovelRepository repository;
    @Mock
    private CategoriaUhRepository categoriaRepository;
    @Mock
    private ReservaRepository reservaRepository;

    private UpdateImovelUseCase useCase;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var mapper = ImovelFixtures.mapper();
        var preenchedor = new PreenchedorImovel(mapper, repository, categoriaRepository, reservaRepository,
                new GovernancaUh(repository, reservaRepository));
        useCase = new UpdateImovelUseCase(repository, mapper, preenchedor);
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservaRepository.findImoveisHospedados()).thenReturn(Set.of());
    }

    private Imovel existente(StatusImovel status) {
        Imovel imovel = ImovelFixtures.imovel(id, status);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(imovel));
        return imovel;
    }

    @Test
    void atualizaDadosESubstituiComodidades() {
        existente(StatusImovel.ATIVO);

        ImovelResponse response = useCase.execute(id, ImovelFixtures.request(null));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Casa da Praia");
        assertThat(response.cidade()).isEqualTo("Ubatuba");
        assertThat(response.comodidades()).containsExactlyInAnyOrder("Wi-Fi", "Piscina");
    }

    @Test
    void mantemStatusAtualQuandoNaoInformado() {
        existente(StatusImovel.INATIVO);

        assertThat(useCase.execute(id, ImovelFixtures.request(null)).status()).isEqualTo(StatusImovel.INATIVO);
    }

    @Test
    void permiteReativarInformandoStatus() {
        existente(StatusImovel.INATIVO);

        assertThat(useCase.execute(id, ImovelFixtures.request(StatusImovel.ATIVO)).status())
                .isEqualTo(StatusImovel.ATIVO);
    }

    @Test
    void formaAntigaMantemCamposDaUhQueNaoVieram() {
        Imovel imovel = existente(StatusImovel.ATIVO);
        CategoriaUh categoria = new CategoriaUh();
        categoria.setId(UUID.randomUUID());
        imovel.setCategoria(categoria);
        imovel.setCapacidadeAdultos(4);
        imovel.setCapacidadeCriancas(2);

        // Total igual ao atual: nada muda.
        ImovelResponse response = useCase.execute(id, ImovelFixtures.requestAntigo(null, 6));
        assertThat(response.categoriaId()).isEqualTo(categoria.getId());
        assertThat(response.tipo()).isEqualTo(TipoUh.CASA);
        assertThat(response.capacidadeAdultos()).isEqualTo(4);
        assertThat(response.capacidadeCriancas()).isEqualTo(2);

        // Total menor: as crianças continuam enquanto couberem, o resto é de adultos.
        response = useCase.execute(id, ImovelFixtures.requestAntigo(null, 3));
        assertThat(response.capacidadeAdultos()).isEqualTo(1);
        assertThat(response.capacidadeCriancas()).isEqualTo(2);
        response = useCase.execute(id, ImovelFixtures.requestAntigo(null, 2));
        assertThat(response.capacidadeAdultos()).isEqualTo(1);
        assertThat(response.capacidadeCriancas()).isEqualTo(1);
    }

    @Test
    void statusManutencaoAntigoComUhOcupadaResponde422RN07() {
        existente(StatusImovel.ATIVO);
        when(reservaRepository.findImoveisHospedados()).thenReturn(Set.of(id));

        assertThatThrownBy(() -> useCase.execute(id, ImovelFixtures.request(StatusImovel.MANUTENCAO)))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting("codigo").isEqualTo("RN-07");
    }

    @Test
    void statusManutencaoAntigoVaiParaAGovernanca() {
        Imovel imovel = existente(StatusImovel.ATIVO);

        assertThat(useCase.execute(id, ImovelFixtures.request(StatusImovel.MANUTENCAO)).status())
                .isEqualTo(StatusImovel.ATIVO);
        assertThat(imovel.getStatusGovernanca()).isEqualTo(StatusGovernanca.MANUTENCAO);
    }

    @Test
    void naoMudaOTipoComReservasAtivas() {
        existente(StatusImovel.ATIVO);
        when(repository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)).thenReturn(Optional.empty());
        when(reservaRepository.existeAtivaNoImovel(id)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(id, ImovelFixtures.requestUh(TipoUh.IMOVEL_COMPLETO, null, "Rua A")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Não é possível mudar o tipo de 'Casa Antiga': há reservas ativas nele");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lancaExcecaoQuandoImovelNaoExiste() {
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, ImovelFixtures.request(null)))
                .isInstanceOf(ImovelNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
