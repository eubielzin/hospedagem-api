package com.gestaocasas.application.usecase.imovel;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.application.dto.ImovelFotoResponse;
import com.gestaocasas.domain.entity.ImovelFoto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.exception.ImovelFotoNotFoundException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelFotoRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FotoPrincipalEListagemUseCasesTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ImovelFotoRepository fotoRepository;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID fotoId = UUID.randomUUID();

    private DefinirFotoPrincipalUseCase definirPrincipal() {
        return new DefinirFotoPrincipalUseCase(imovelRepository, fotoRepository, ImovelFixtures.mapper());
    }

    private static ImovelFoto foto(String url, int ordem, boolean principal) {
        ImovelFoto foto = new ImovelFoto();
        foto.setId(UUID.randomUUID());
        foto.setUrl(url);
        foto.setOrdem(ordem);
        foto.setPrincipal(principal);
        return foto;
    }

    @Test
    void definePrincipalDepoisDeDesmarcarAAnterior() {
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        ImovelFoto foto = foto("https://exemplo.com/a.jpg", 1, false);
        when(fotoRepository.findByIdAndImovelIdAndExclusaoPendenteEmIsNull(fotoId, imovelId))
                .thenReturn(Optional.of(foto));
        when(fotoRepository.saveAndFlush(foto)).thenReturn(foto);

        ImovelFotoResponse response = definirPrincipal().execute(imovelId, fotoId);

        assertThat(response.principal()).isTrue();
        InOrder ordem = inOrder(imovelRepository, fotoRepository);
        ordem.verify(imovelRepository).findByIdForUpdate(imovelId);
        ordem.verify(fotoRepository).desmarcarPrincipal(imovelId);
        ordem.verify(fotoRepository).saveAndFlush(foto);
    }

    @Test
    void naoDefinePrincipalComFotoDeOutroImovelOuEmExclusao() {
        when(imovelRepository.findByIdForUpdate(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        when(fotoRepository.findByIdAndImovelIdAndExclusaoPendenteEmIsNull(fotoId, imovelId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> definirPrincipal().execute(imovelId, fotoId))
                .isInstanceOf(ImovelFotoNotFoundException.class);
        verify(fotoRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDefinePrincipalEmImovelInexistente() {
        when(imovelRepository.findByIdForUpdate(imovelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> definirPrincipal().execute(imovelId, fotoId))
                .isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(fotoRepository);
    }

    @Test
    void listaFotosNaOrdemDevolvidaPeloRepositorio() {
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(fotoRepository.findByImovelIdAndExclusaoPendenteEmIsNullOrderByOrdemAscCriadoEmAsc(imovelId))
                .thenReturn(List.of(foto("https://exemplo.com/0.jpg", 0, true),
                        foto("https://exemplo.com/1.jpg", 1, false)));

        List<ImovelFotoResponse> fotos = new ListarFotosImovelUseCase(imovelRepository, fotoRepository,
                ImovelFixtures.mapper()).execute(imovelId);

        assertThat(fotos).extracting(ImovelFotoResponse::ordem).containsExactly(0, 1);
        assertThat(fotos).extracting(ImovelFotoResponse::principal).containsExactly(true, false);
    }

    @Test
    void listarFotosDeImovelInexistente() {
        when(imovelRepository.existsById(imovelId)).thenReturn(false);

        assertThatThrownBy(() -> new ListarFotosImovelUseCase(imovelRepository, fotoRepository,
                ImovelFixtures.mapper()).execute(imovelId)).isInstanceOf(ImovelNotFoundException.class);
    }
}
