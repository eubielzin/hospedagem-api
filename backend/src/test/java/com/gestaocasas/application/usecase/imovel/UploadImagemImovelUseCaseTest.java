package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImagemFixtures;
import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.application.mapper.ImovelMapperImpl;
import com.gestaocasas.application.validation.ImagemValidator;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StorageProvider;
import com.gestaocasas.domain.exception.ArquivoInvalidoException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.storage.StoragePort;
import com.gestaocasas.domain.storage.StorageUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadImagemImovelUseCaseTest {

    private static final String BASE = "https://projeto.supabase.co/storage/v1/object/public/imoveis/";

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ImovelFotoRepository fotoRepository;
    @Mock
    private StoragePort storage;

    private final UUID imovelId = UUID.randomUUID();
    private final byte[] png = ImagemFixtures.png(800, 600);
    private UploadImagemImovelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadImagemImovelUseCase(imovelRepository, fotoRepository,
                new ImagemValidator(DataSize.ofMegabytes(5), 8000, 40_000_000L), storage, new ImovelMapperImpl(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private void imovelExistente() {
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
    }

    private void storageAceita() {
        when(storage.upload(anyString(), any(), anyString())).thenAnswer(invocation ->
                new StorageUploadResult(invocation.getArgument(0), BASE + invocation.getArgument(0)));
    }

    private void persistenciaFunciona() {
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        when(fotoRepository.saveAndFlush(any(ImovelFoto.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enviaParaOStorageEGravaAReferencia() {
        imovelExistente();
        storageAceita();
        persistenciaFunciona();
        when(fotoRepository.existsByImovelIdAndPrincipalTrue(imovelId)).thenReturn(true);
        when(fotoRepository.findMaiorOrdem(imovelId)).thenReturn(2);

        ImovelFotoResponse response = useCase.execute(imovelId, png, "image/png", "  Fachada ", false);

        ArgumentCaptor<String> caminho = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(caminho.capture(), eq(png), eq("image/png"));
        assertThat(caminho.getValue()).matches(imovelId + "/[0-9a-f-]{36}\\.png");

        ArgumentCaptor<ImovelFoto> foto = ArgumentCaptor.forClass(ImovelFoto.class);
        verify(fotoRepository).saveAndFlush(foto.capture());
        assertThat(foto.getValue().getStorageProvider()).isEqualTo(StorageProvider.SUPABASE);
        assertThat(foto.getValue().getStoragePath()).isEqualTo(caminho.getValue());
        assertThat(foto.getValue().getImovel().getId()).isEqualTo(imovelId);

        assertThat(response.url()).isEqualTo(BASE + caminho.getValue());
        assertThat(response.descricao()).isEqualTo("Fachada");
        assertThat(response.ordem()).isEqualTo(3);
        assertThat(response.principal()).isFalse();
        verify(fotoRepository, never()).desmarcarPrincipal(any());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void extensaoVemDoFormatoValidadoENaoDoCliente() {
        imovelExistente();
        storageAceita();
        persistenciaFunciona();

        useCase.execute(imovelId, ImagemFixtures.jpeg(10, 10), "image/jpeg", null, false);
        useCase.execute(imovelId, ImagemFixtures.webp(10, 10), "image/webp", null, false);

        verify(storage).upload(org.mockito.ArgumentMatchers.endsWith(".jpg"), any(), eq("image/jpeg"));
        verify(storage).upload(org.mockito.ArgumentMatchers.endsWith(".webp"), any(), eq("image/webp"));
    }

    @Test
    void primeiraFotoDoImovelViraPrincipal() {
        imovelExistente();
        storageAceita();
        persistenciaFunciona();
        when(fotoRepository.existsByImovelIdAndPrincipalTrue(imovelId)).thenReturn(false);
        when(fotoRepository.findMaiorOrdem(imovelId)).thenReturn(-1);

        ImovelFotoResponse response = useCase.execute(imovelId, png, "image/png", null, false);

        assertThat(response.principal()).isTrue();
        assertThat(response.ordem()).isZero();
    }

    @Test
    void uploadComoPrincipalDesmarcaAAnterior() {
        imovelExistente();
        storageAceita();
        persistenciaFunciona();

        ImovelFotoResponse response = useCase.execute(imovelId, png, "image/png", null, true);

        assertThat(response.principal()).isTrue();
        verify(fotoRepository).desmarcarPrincipal(imovelId);
    }

    @Test
    void imovelInexistenteNaoChegaAoStorage() {
        when(imovelRepository.existsById(imovelId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(imovelId, png, "image/png", null, false))
                .isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(storage, fotoRepository);
    }

    @Test
    void arquivoInvalidoNaoChegaAoStorage() {
        imovelExistente();

        assertThatThrownBy(() -> useCase.execute(imovelId, new byte[0], "image/png", null, false))
                .isInstanceOf(ArquivoInvalidoException.class);
        assertThatThrownBy(() -> useCase.execute(imovelId, "GIF89a...".getBytes(), "image/png", null, false))
                .isInstanceOf(ArquivoInvalidoException.class);
        verifyNoInteractions(storage, fotoRepository);
    }

    @Test
    void falhaNoStorageNaoGravaNada() {
        imovelExistente();
        when(storage.upload(anyString(), any(), anyString())).thenThrow(new StorageException("fora do ar"));

        assertThatThrownBy(() -> useCase.execute(imovelId, png, "image/png", null, false))
                .isInstanceOf(StorageException.class);
        verifyNoInteractions(fotoRepository);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void falhaAoGravarRemoveOObjetoRecemEnviado() {
        imovelExistente();
        storageAceita();
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        when(fotoRepository.saveAndFlush(any(ImovelFoto.class)))
                .thenThrow(new DataIntegrityViolationException("uk_imovel_foto_principal"));

        assertThatThrownBy(() -> useCase.execute(imovelId, png, "image/png", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(enviado.capture(), any(), anyString());
        verify(storage).delete(enviado.getValue());
    }

    @Test
    void imovelRemovidoDuranteOUploadTambemCompensa() {
        imovelExistente();
        storageAceita();
        when(imovelRepository.findByIdForUpdate(imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(imovelId, png, "image/png", null, false))
                .isInstanceOf(ImovelNotFoundException.class);
        verify(storage).delete(anyString());
    }

    @Test
    void falhaNaCompensacaoMantemOErroOriginal() {
        imovelExistente();
        storageAceita();
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        when(fotoRepository.saveAndFlush(any(ImovelFoto.class)))
                .thenThrow(new DataIntegrityViolationException("falha no banco"));
        doThrow(new StorageException("fora do ar")).when(storage).delete(anyString());

        assertThatThrownBy(() -> useCase.execute(imovelId, png, "image/png", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
