package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapperImpl;
import com.gestaocasas.application.validation.ProdutoUnicidadeValidator;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.repository.ProdutoRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateProdutoUseCaseTest {

    @Mock
    private ProdutoRepository repository;

    private UpdateProdutoUseCase useCase;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpdateProdutoUseCase(repository, new ProdutoMapperImpl(),
                new ProdutoUnicidadeValidator(repository));
        lenient().when(repository.saveAndFlush(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void atualizaDadosDoProduto() {
        when(repository.findById(id)).thenReturn(Optional.of(ProdutoFixtures.produto(id, true)));

        ProdutoResponse response = useCase.execute(id, ProdutoFixtures.request(null));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.codigo()).isEqualTo("COCA350");
        assertThat(response.nome()).isEqualTo("Coca-Cola Lata 350ml");
        assertThat(response.precoVenda()).isEqualByComparingTo("6.00");
    }

    @Test
    void mantemSituacaoAtualQuandoNaoInformada() {
        when(repository.findById(id)).thenReturn(Optional.of(ProdutoFixtures.produto(id, false)));

        assertThat(useCase.execute(id, ProdutoFixtures.request(null)).ativo()).isFalse();
    }

    @Test
    void permiteReativarInformandoAtivo() {
        when(repository.findById(id)).thenReturn(Optional.of(ProdutoFixtures.produto(id, false)));

        assertThat(useCase.execute(id, ProdutoFixtures.request(true)).ativo()).isTrue();
    }

    @Test
    void recusaCodigoDeOutroProdutoSemAlterarAEntidade() {
        Produto existente = ProdutoFixtures.produto(id, true);
        when(repository.findById(id)).thenReturn(Optional.of(existente));
        when(repository.existsByCodigoIgnoreCaseAndIdNot("COCA350", id)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(id, ProdutoFixtures.request(null)))
                .isInstanceOf(ConflitoException.class);
        assertThat(existente.getCodigo()).isEqualTo("AGUA500");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lancaExcecaoQuandoProdutoNaoExiste() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, ProdutoFixtures.request(null)))
                .isInstanceOf(ProdutoNotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
