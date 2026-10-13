package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.EstoqueMinimoRequest;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefinirEstoqueMinimoUseCaseTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private EstoqueImovelRepository estoqueRepository;

    private DefinirEstoqueMinimoUseCase useCase;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new DefinirEstoqueMinimoUseCase(imovelRepository, produtoRepository, estoqueRepository,
                new EstoqueMapper());
    }

    private EstoqueImovel saldo(Produto produto, int quantidade, int minimo) {
        EstoqueImovel estoque = new EstoqueImovel();
        estoque.setImovel(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO));
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(quantidade);
        estoque.setEstoqueMinimo(minimo);
        return estoque;
    }

    @Test
    void criaALinhaDeSaldoSeFaltarEAlteraSomenteOMinimo() {
        Produto produto = ProdutoFixtures.produto(produtoId, true);
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        when(estoqueRepository.findForUpdate(imovelId, produtoId)).thenReturn(Optional.of(saldo(produto, 3, 0)));
        when(estoqueRepository.saveAndFlush(any(EstoqueImovel.class))).thenAnswer(inv -> inv.getArgument(0));

        EstoqueItemResponse response = useCase.execute(imovelId, produtoId, new EstoqueMinimoRequest(5));

        InOrder ordem = inOrder(estoqueRepository);
        ordem.verify(estoqueRepository).criarSeAusente(imovelId, produtoId);
        ordem.verify(estoqueRepository).findForUpdate(imovelId, produtoId);
        assertThat(response.imovelId()).isEqualTo(imovelId);
        assertThat(response.produtoId()).isEqualTo(produtoId);
        assertThat(response.estoqueMinimo()).isEqualTo(5);
        assertThat(response.quantidadeAtual()).isEqualTo(3);
        assertThat(response.abaixoDoMinimo()).isTrue();
    }

    @Test
    void recusaProdutoQueNaoControlaEstoque() {
        Produto taxa = ProdutoFixtures.produto(produtoId, true);
        taxa.setControlaEstoque(false);
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(taxa));

        assertThatThrownBy(() -> useCase.execute(imovelId, produtoId, new EstoqueMinimoRequest(5)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não controla estoque");
        verifyNoInteractions(estoqueRepository);
    }

    @Test
    void lancaExcecaoQuandoImovelOuProdutoNaoExiste() {
        when(imovelRepository.existsById(imovelId)).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute(imovelId, produtoId, new EstoqueMinimoRequest(1)))
                .isInstanceOf(ImovelNotFoundException.class);

        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(imovelId, produtoId, new EstoqueMinimoRequest(1)))
                .isInstanceOf(ProdutoNotFoundException.class);
        verifyNoInteractions(estoqueRepository);
    }
}
