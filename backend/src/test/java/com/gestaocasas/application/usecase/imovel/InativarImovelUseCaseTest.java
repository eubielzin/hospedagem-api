package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
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
class InativarImovelUseCaseTest {

    @Mock
    private ImovelRepository repository;

    private InativarImovelUseCase useCase;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new InativarImovelUseCase(repository, new ImovelMapperImpl());
    }

    @Test
    void marcaImovelComoInativoSemExcluir() {
        when(repository.findById(id)).thenReturn(Optional.of(ImovelFixtures.imovel(id, StatusImovel.ATIVO)));
        when(repository.saveAndFlush(any(Imovel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(useCase.execute(id).status()).isEqualTo(StatusImovel.INATIVO);
        verify(repository, never()).delete(any(Imovel.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void lancaExcecaoQuandoImovelNaoExiste() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(ImovelNotFoundException.class);
    }
}
