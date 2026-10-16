package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetImovelByIdUseCaseTest {

    @Mock
    private ImovelRepository repository;

    private GetImovelByIdUseCase useCase;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new GetImovelByIdUseCase(repository, ImovelFixtures.mapper());
    }

    @Test
    void retornaImovelExistente() {
        when(repository.findById(id)).thenReturn(Optional.of(ImovelFixtures.imovel(id, StatusImovel.ATIVO)));

        assertThat(useCase.execute(id).nome()).isEqualTo("Casa Antiga");
    }

    @Test
    void lancaExcecaoQuandoImovelNaoExiste() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(ImovelNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
