package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StorageProvider;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.storage.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcluirImagemImovelUseCaseTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ImovelFotoRepository fotoRepository;
    @Mock
    private StoragePort storage;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID fotoId = UUID.randomUUID();
    private final String objectPath = imovelId + "/" + UUID.randomUUID() + ".webp";
    private ExcluirImagemImovelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExcluirImagemImovelUseCase(imovelRepository, fotoRepository, storage,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private void imovelExistente() {
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
    }

    private ImovelFoto fotoNoStorage(boolean principal) {
        ImovelFoto foto = new ImovelFoto();
        foto.setStorageProvider(StorageProvider.SUPABASE);
        foto.setStoragePath(objectPath);
        foto.setPrincipal(principal);
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.of(foto));
        return foto;
    }

    @Test
    void fotoPorUrlEApagadaSemTocarNoStorage() {
        imovelExistente();
        ImovelFoto legada = new ImovelFoto();
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.of(legada));

        useCase.execute(imovelId, fotoId);

        verify(fotoRepository).delete(legada);
        verifyNoInteractions(storage);
    }

    @Test
    void fotoDoStorageEMarcadaRemovidaDoStorageEDepoisApagada() {
        imovelExistente();
        ImovelFoto foto = fotoNoStorage(false);

        useCase.execute(imovelId, fotoId);

        assertThat(foto.getExclusaoPendenteEm()).isNotNull();
        InOrder ordem = inOrder(fotoRepository, storage);
        ordem.verify(fotoRepository).findByIdAndImovelId(fotoId, imovelId);
        ordem.verify(storage).delete(objectPath);
        ordem.verify(fotoRepository).deleteById(fotoId);
    }

    @Test
    void falhaNoStorageMantemORegistroComoExclusaoPendente() {
        imovelExistente();
        ImovelFoto foto = fotoNoStorage(false);
        doThrow(new StorageException("fora do ar")).when(storage).delete(objectPath);

        assertThatThrownBy(() -> useCase.execute(imovelId, fotoId)).isInstanceOf(StorageException.class);

        assertThat(foto.getExclusaoPendenteEm()).isNotNull();
        assertThat(foto.getStoragePath()).isEqualTo(objectPath);
        verify(fotoRepository, never()).deleteById(any());
        verify(fotoRepository, never()).delete(any(ImovelFoto.class));
    }

    @Test
    void repetirAExclusaoPendenteConcluiSemAlterarADataDoPedido() {
        imovelExistente();
        ImovelFoto foto = fotoNoStorage(false);
        Instant pedidoOriginal = Instant.parse("2026-10-01T10:00:00Z");
        foto.setExclusaoPendenteEm(pedidoOriginal);

        useCase.execute(imovelId, fotoId);

        assertThat(foto.getExclusaoPendenteEm()).isEqualTo(pedidoOriginal);
        verify(storage).delete(objectPath);
        verify(fotoRepository).deleteById(fotoId);
    }

    @Test
    void excluirAPrincipalPromoveAProximaDaOrdem() {
        imovelExistente();
        ImovelFoto principal = fotoNoStorage(true);
        ImovelFoto proxima = new ImovelFoto();
        when(fotoRepository.findFirstByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId))
                .thenReturn(Optional.of(proxima));

        useCase.execute(imovelId, fotoId);

        assertThat(principal.isPrincipal()).isFalse();
        assertThat(proxima.isPrincipal()).isTrue();
        InOrder ordem = inOrder(fotoRepository);
        ordem.verify(fotoRepository).flush();
        ordem.verify(fotoRepository).findFirstByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId);
    }

    @Test
    void excluirAUnicaFotoDeixaOImovelSemPrincipal() {
        imovelExistente();
        ImovelFoto legada = new ImovelFoto();
        legada.setPrincipal(true);
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.of(legada));
        when(fotoRepository.findFirstByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId))
                .thenReturn(Optional.empty());

        useCase.execute(imovelId, fotoId);

        verify(fotoRepository).delete(legada);
    }

    @Test
    void naoExcluiFotoDeOutroImovel() {
        imovelExistente();
        when(fotoRepository.findByIdAndImovelId(fotoId, imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(imovelId, fotoId)).isInstanceOf(ImovelFotoNotFoundException.class);

        verify(fotoRepository, never()).delete(any(ImovelFoto.class));
        verify(fotoRepository, never()).deleteById(any());
        verifyNoInteractions(storage);
    }

    @Test
    void imovelInexistente() {
        when(imovelRepository.findByIdForUpdate(imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(imovelId, fotoId)).isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(fotoRepository, storage);
    }
}
