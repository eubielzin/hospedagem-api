package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelResponse;
import com.gestaocasas.application.mapper.ImovelMapperImpl;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateImovelUseCaseTest {

    @Mock
    private ImovelRepository repository;

    private CreateImovelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateImovelUseCase(repository, new ImovelMapperImpl());
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void criaImovelAtivoQuandoStatusNaoInformado() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(null));

        ArgumentCaptor<Imovel> captor = ArgumentCaptor.forClass(Imovel.class);
        verify(repository).saveAndFlush(captor.capture());
        Imovel salvo = captor.getValue();
        assertThat(salvo.getStatus()).isEqualTo(StatusImovel.ATIVO);
        assertThat(salvo.getNome()).isEqualTo("Casa da Praia");
        assertThat(salvo.getValorBaseDiaria()).isEqualByComparingTo("450.00");
        assertThat(response.status()).isEqualTo(StatusImovel.ATIVO);
    }

    @Test
    void normalizaUfECopiaComodidadesSemEspacos() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(null));

        assertThat(response.uf()).isEqualTo("SP");
        assertThat(response.comodidades()).containsExactlyInAnyOrder("Wi-Fi", "Piscina");
    }

    @Test
    void respeitaStatusInformado() {
        ImovelResponse response = useCase.execute(ImovelFixtures.request(StatusImovel.MANUTENCAO));

        assertThat(response.status()).isEqualTo(StatusImovel.MANUTENCAO);
    }
}
