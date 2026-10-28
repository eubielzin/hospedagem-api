package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.CategoriaUhNotFoundException;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.BloqueioRepository;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalTime;
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
class CreateImovelUseCaseTest {

    @Mock
    private ImovelRepository repository;
    @Mock
    private CategoriaUhRepository categoriaRepository;
    @Mock
    private ReservaRepository reservaRepository;

    private CreateImovelUseCase useCase;

    @BeforeEach
    void setUp() {
        var mapper = ImovelFixtures.mapper();
        var preenchedor = new PreenchedorImovel(mapper, repository, categoriaRepository, reservaRepository,
                new GovernancaUh(repository, reservaRepository,
                        org.mockito.Mockito.mock(BloqueioRepository.class), Clock.systemUTC()));
        useCase = new CreateImovelUseCase(repository, mapper, preenchedor);
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservaRepository.findImoveisHospedados()).thenReturn(Set.of());
    }

    private Imovel salvo() {
        ArgumentCaptor<Imovel> captor = ArgumentCaptor.forClass(Imovel.class);
        verify(repository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void criaCasaAtivaELivreQuandoStatusNaoInformado() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(null));

        Imovel salvo = salvo();
        assertThat(salvo.getStatus()).isEqualTo(StatusImovel.ATIVO);
        assertThat(salvo.getStatusGovernanca()).isEqualTo(StatusGovernanca.LIVRE);
        assertThat(salvo.getTipo()).isEqualTo(TipoUh.CASA);
        assertThat(salvo.getValorBaseDiaria()).isEqualByComparingTo("450.00");
        assertThat(response.status()).isEqualTo(StatusImovel.ATIVO);
        assertThat(response.capacidadeAdultos()).isEqualTo(6);
        assertThat(response.capacidadeCriancas()).isEqualTo(2);
        assertThat(response.capacidadeMaxima()).isEqualTo(8);
    }

    @Test
    void normalizaUfECopiaComodidadesSemEspacos() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(null));

        assertThat(response.uf()).isEqualTo("SP");
        assertThat(response.comodidades()).containsExactlyInAnyOrder("Wi-Fi", "Piscina");
    }

    @Test
    void formaAntigaComSoOTotalViraCapacidadeDeAdultos() {
        ImovelResponse response = useCase.execute(ImovelFixtures.requestAntigo(null, 5));

        assertThat(response.capacidadeAdultos()).isEqualTo(5);
        assertThat(response.capacidadeCriancas()).isZero();
        assertThat(response.capacidadeMaxima()).isEqualTo(5);
    }

    @Test
    void statusManutencaoAntigoViraAtivoComGovernancaEmManutencao() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(StatusImovel.MANUTENCAO));

        assertThat(response.status()).isEqualTo(StatusImovel.ATIVO);
        assertThat(salvo().getStatusGovernanca()).isEqualTo(StatusGovernanca.MANUTENCAO);
    }

    @Test
    void semHorarioProprioRespondeComOPadraoDaPousada() {
        ImovelResponse response = useCase.execute(ImovelFixtures.requestUh(TipoUh.CASA, null, "Rua A"));

        assertThat(salvo().getHorarioCheckin()).isNull();
        assertThat(response.horarioCheckin()).isEqualTo(LocalTime.of(14, 0));
        assertThat(response.horarioCheckout()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void vinculaACategoriaInformada() {
        UUID categoriaId = UUID.randomUUID();
        CategoriaUh categoria = new CategoriaUh();
        categoria.setId(categoriaId);
        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoria));

        assertThat(useCase.execute(ImovelFixtures.requestUh(TipoUh.CASA, categoriaId, "Rua A")).categoriaId())
                .isEqualTo(categoriaId);
    }

    @Test
    void categoriaInexistenteResponde404() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ImovelFixtures.requestUh(TipoUh.CASA, categoriaId, "Rua A")))
                .isInstanceOf(CategoriaUhNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void imovelCompletoSemEnderecoRecebeODaPrimeiraCasa() {
        Imovel casa = ImovelFixtures.imovel(UUID.randomUUID(), StatusImovel.ATIVO);
        casa.setNumero("10");
        casa.setCep("65590-000");
        when(repository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)).thenReturn(Optional.empty());
        when(repository.findFirstByTipoOrderByCriadoEmAsc(TipoUh.CASA)).thenReturn(Optional.of(casa));

        ImovelResponse response = useCase.execute(ImovelFixtures.requestUh(TipoUh.IMOVEL_COMPLETO, null, null));

        assertThat(response.tipo()).isEqualTo(TipoUh.IMOVEL_COMPLETO);
        assertThat(response.logradouro()).isEqualTo("Rua A");
        assertThat(response.numero()).isEqualTo("10");
        assertThat(response.cidade()).isEqualTo("Santos");
        assertThat(response.uf()).isEqualTo("SP");
        assertThat(response.cep()).isEqualTo("65590-000");
    }

    @Test
    void imovelCompletoSemEnderecoESemCasaResponde422() {
        when(repository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)).thenReturn(Optional.empty());
        when(repository.findFirstByTipoOrderByCriadoEmAsc(TipoUh.CASA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ImovelFixtures.requestUh(TipoUh.IMOVEL_COMPLETO, null, null)))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void segundoImovelCompletoResponde409() {
        Imovel existente = ImovelFixtures.imovel(UUID.randomUUID(), StatusImovel.ATIVO);
        existente.setNome("Pousada inteira");
        when(repository.findFirstByTipo(TipoUh.IMOVEL_COMPLETO)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> useCase.execute(ImovelFixtures.requestUh(TipoUh.IMOVEL_COMPLETO, null, "Rua B")))
                .isInstanceOf(ConflitoException.class)
                .hasMessage("Já existe um imóvel completo: 'Pousada inteira'");
        verify(repository, never()).saveAndFlush(any());
    }
}
