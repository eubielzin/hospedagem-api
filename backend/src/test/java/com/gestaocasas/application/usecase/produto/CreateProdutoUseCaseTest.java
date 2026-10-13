package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.dto.ProdutoResponse;
import com.gestaocasas.application.mapper.ProdutoMapperImpl;
import com.gestaocasas.application.validation.ProdutoUnicidadeValidator;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProdutoUseCaseTest {

    @Mock
    private ProdutoRepository repository;

    private CreateProdutoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProdutoUseCase(repository, new ProdutoMapperImpl(),
                new ProdutoUnicidadeValidator(repository));
        lenient().when(repository.saveAndFlush(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void criaProdutoAtivoQuandoSituacaoNaoInformada() {
        ProdutoResponse response = useCase.execute(ProdutoFixtures.request(null));

        assertThat(response.ativo()).isTrue();
        assertThat(response.controlaEstoque()).isTrue();
        assertThat(response.precoVenda()).isEqualByComparingTo("6.00");
        assertThat(response.precoCusto()).isEqualByComparingTo("3.20");
    }

    @Test
    void respeitaSituacaoInformada() {
        assertThat(useCase.execute(ProdutoFixtures.request(false)).ativo()).isFalse();
    }

    @Test
    void normalizaTextosEUnidade() {
        ProdutoResponse response = useCase.execute(ProdutoFixtures.request(null));

        assertThat(response.codigo()).isEqualTo("COCA350");
        assertThat(response.codigoBarras()).isEqualTo("7894900010015");
        assertThat(response.nome()).isEqualTo("Coca-Cola Lata 350ml");
        assertThat(response.descricao()).isNull();
        assertThat(response.unidade()).isEqualTo("UN");
    }

    @Test
    void recusaCodigoJaUsadoIgnorandoMaiusculas() {
        when(repository.existsByCodigoIgnoreCase("COCA350")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(ProdutoFixtures.request(null)))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("código 'COCA350'");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recusaCodigoDeBarrasJaUsado() {
        when(repository.existsByCodigoBarras("7894900010015")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(ProdutoFixtures.request(null)))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("código de barras");
        verify(repository, never()).saveAndFlush(any());
    }
}
