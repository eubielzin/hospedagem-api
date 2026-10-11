package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.mapper.ImovelMapperImpl;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateImovelUseCaseTest {

    @Mock
    private ImovelRepository repository;

    private UpdateImovelUseCase useCase;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpdateImovelUseCase(repository, new ImovelMapperImpl());
    }

    @Test
    void atualizaDadosESubstituiComodidades() {
        when(repository.findById(id)).thenReturn(Optional.of(ImovelFixtures.imovel(id, StatusImovel.ATIVO)));
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImovelResponse response = useCase.execute(id, ImovelFixtures.request(null));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Casa da Praia");
        assertThat(response.cidade()).isEqualTo("Ubatuba");
        assertThat(response.comodidades()).containsExactlyInAnyOrder("Wi-Fi", "Piscina");
    }

    @Test
    void mantemStatusAtualQuandoNaoInformado() {
        when(repository.findById(id)).thenReturn(Optional.of(ImovelFixtures.imovel(id, StatusImovel.MANUTENCAO)));
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.execute(id, ImovelFixtures.request(null)).status()).isEqualTo(StatusImovel.MANUTENCAO);
    }

    @Test
    void permiteReativarInformandoStatus() {
        when(repository.findById(id)).thenReturn(Optional.of(ImovelFixtures.imovel(id, StatusImovel.INATIVO)));
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.execute(id, ImovelFixtures.request(StatusImovel.ATIVO)).status())
                .isEqualTo(StatusImovel.ATIVO);
    }

    @Test
    void lancaExcecaoQuandoImovelNaoExiste() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, ImovelFixtures.request(null)))
                .isInstanceOf(ImovelNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
