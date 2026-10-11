package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelFotoRequest;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapperImpl;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ImovelFotoUseCasesTest {

    @Mock
    private ImovelRepository imovelRepository;

    @Mock
    private ImovelFotoRepository fotoRepository;

    private final UUID imovelId = UUID.randomUUID();

    private AddImovelFotoUseCase addUseCase() {
        return new AddImovelFotoUseCase(imovelRepository, fotoRepository, new ImovelMapperImpl());
    }

    @Test
    void adicionaFotoNoFimQuandoOrdemNaoInformada() {
        Imovel imovel = ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO);
        when(imovelRepository.findById(imovelId)).thenReturn(Optional.of(imovel));
        when(fotoRepository.countByImovelId(imovelId)).thenReturn(2L);
        when(fotoRepository.save(any(ImovelFoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImovelFotoResponse response = addUseCase()
                .execute(imovelId, new ImovelFotoRequest("https://exemplo.com/a.jpg", "Fachada", null));

        ArgumentCaptor<ImovelFoto> captor = ArgumentCaptor.forClass(ImovelFoto.class);
        verify(fotoRepository).save(captor.capture());
        assertThat(captor.getValue().getImovel()).isSameAs(imovel);
        assertThat(response.ordem()).isEqualTo(2);
        assertThat(response.url()).isEqualTo("https://exemplo.com/a.jpg");
    }

    @Test
    void respeitaOrdemInformada() {
        when(imovelRepository.findById(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        when(fotoRepository.save(any(ImovelFoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImovelFotoResponse response = addUseCase()
                .execute(imovelId, new ImovelFotoRequest("https://exemplo.com/a.jpg", null, 0));

        assertThat(response.ordem()).isZero();
        verify(fotoRepository, never()).countByImovelId(any());
    }

    @Test
    void naoAdicionaFotoEmImovelInexistente() {
        when(imovelRepository.findById(imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addUseCase()
                .execute(imovelId, new ImovelFotoRequest("https://exemplo.com/a.jpg", null, null)))
                .isInstanceOf(ImovelNotFoundException.class);
        verify(fotoRepository, never()).save(any());
    }

    @Test
    void removeFotoDoImovel() {
        UUID fotoId = UUID.randomUUID();
        ImovelFoto foto = new ImovelFoto();
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.of(foto));

        new RemoveImovelFotoUseCase(fotoRepository).execute(imovelId, fotoId);

        verify(fotoRepository).delete(foto);
    }

    @Test
    void naoRemoveFotoDeOutroImovel() {
        UUID fotoId = UUID.randomUUID();
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RemoveImovelFotoUseCase(fotoRepository).execute(imovelId, fotoId))
                .isInstanceOf(ImovelFotoNotFoundException.class);
        verify(fotoRepository, never()).delete(any());
    }
}
