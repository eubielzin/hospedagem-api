package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.mapper.ProdutoMapperImpl;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InativarEConsultarProdutoUseCasesTest {

    @Mock
    private ProdutoRepository repository;

    private final ProdutoMapperImpl mapper = new ProdutoMapperImpl();
    private final UUID id = UUID.randomUUID();

    @Test
    void inativaProdutoSemApagar() {
        when(repository.findById(id)).thenReturn(Optional.of(ProdutoFixtures.produto(id, true)));
        when(repository.saveAndFlush(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(new InativarProdutoUseCase(repository, mapper).execute(id).ativo()).isFalse();
    }

    @Test
    void inativarProdutoInexistenteLancaExcecao() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new InativarProdutoUseCase(repository, mapper).execute(id))
                .isInstanceOf(ProdutoNotFoundException.class);
    }

    @Test
    void consultaProdutoPorId() {
        when(repository.findById(id)).thenReturn(Optional.of(ProdutoFixtures.produto(id, true)));

        assertThat(new GetProdutoByIdUseCase(repository, mapper).execute(id).nome()).isEqualTo("Água Mineral 500ml");
    }

    @Test
    void consultarProdutoInexistenteLancaExcecao() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetProdutoByIdUseCase(repository, mapper).execute(id))
                .isInstanceOf(ProdutoNotFoundException.class)
                .hasMessage("Produto não encontrado: " + id);
    }
}
