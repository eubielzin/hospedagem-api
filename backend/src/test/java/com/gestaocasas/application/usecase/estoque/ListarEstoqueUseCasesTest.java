package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.dto.EstoqueItemResponse;
import com.gestaocasas.application.dto.PageResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoComEstoque;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarEstoqueUseCasesTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private EstoqueImovelRepository estoqueRepository;

    private final EstoqueMapper mapper = new EstoqueMapper();
    private final UUID imovelId = UUID.randomUUID();

    private static ProdutoComEstoque item(Produto produto, EstoqueImovel estoque) {
        return new ProdutoComEstoque() {
            @Override
            public Produto getProduto() {
                return produto;
            }

            @Override
            public EstoqueImovel getEstoque() {
                return estoque;
            }
        };
    }

    private static EstoqueImovel saldo(Produto produto, int quantidade, int minimo) {
        EstoqueImovel estoque = new EstoqueImovel();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(quantidade);
        estoque.setEstoqueMinimo(minimo);
        return estoque;
    }

    @Test
    void produtoSemSaldoNoImovelApareceZerado() {
        Produto comSaldo = ProdutoFixtures.produto(UUID.randomUUID(), true);
        Produto semSaldo = ProdutoFixtures.produto(UUID.randomUUID(), true);
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        Page<ProdutoComEstoque> pagina = new PageImpl<>(
                List.of(item(comSaldo, saldo(comSaldo, 20, 5)), item(semSaldo, null)));
        when(estoqueRepository.listarPorImovel(eq(imovelId), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(pagina);

        PageResponse<EstoqueItemResponse> response = new ListarEstoqueImovelUseCase(imovelRepository,
                estoqueRepository, mapper).execute(imovelId, null, null, PageRequest.of(0, 20));

        assertThat(response.content()).extracting(EstoqueItemResponse::quantidadeAtual).containsExactly(20, 0);
        assertThat(response.content()).extracting(EstoqueItemResponse::abaixoDoMinimo).containsExactly(false, false);
        assertThat(response.content().get(1).atualizadoEm()).isNull();
        assertThat(response.content()).allMatch(itemResposta -> itemResposta.imovelId().equals(imovelId));
    }

    @Test
    void montaFiltrosEDescartaOrdenacaoDoCliente() {
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(estoqueRepository.listarPorImovel(any(), anyString(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());
        ListarEstoqueImovelUseCase useCase = new ListarEstoqueImovelUseCase(imovelRepository, estoqueRepository, mapper);

        useCase.execute(imovelId, " Coca_50% ", true, PageRequest.of(2, 10, Sort.by("qualquer")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(estoqueRepository).listarPorImovel(eq(imovelId), eq("%coca\\_50\\%%"), eq(false), eq(true),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();

        useCase.execute(imovelId, "  ", null, PageRequest.of(0, 20));
        verify(estoqueRepository).listarPorImovel(eq(imovelId), eq("%"), eq(true), eq(false), any());
    }

    @Test
    void alertasTrazemOSaldoDoImovel() {
        Produto produto = ProdutoFixtures.produto(UUID.randomUUID(), true);
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(estoqueRepository.listarAlertas(eq(imovelId), any()))
                .thenReturn(new PageImpl<>(List.of(saldo(produto, 2, 5))));

        PageResponse<EstoqueItemResponse> response = new ListarAlertasEstoqueUseCase(imovelRepository,
                estoqueRepository, mapper).execute(imovelId, PageRequest.of(0, 20));

        assertThat(response.content()).singleElement().satisfies(alerta -> {
            assertThat(alerta.quantidadeAtual()).isEqualTo(2);
            assertThat(alerta.estoqueMinimo()).isEqualTo(5);
            assertThat(alerta.abaixoDoMinimo()).isTrue();
        });
    }

    @Test
    void regraDoAlerta() {
        assertThat(EstoqueImovel.abaixoDoMinimo(0, 0)).isFalse();
        assertThat(EstoqueImovel.abaixoDoMinimo(6, 5)).isFalse();
        assertThat(EstoqueImovel.abaixoDoMinimo(5, 5)).isTrue();
        assertThat(EstoqueImovel.abaixoDoMinimo(0, 5)).isTrue();
    }

    @Test
    void imovelInexistenteLancaExcecao() {
        when(imovelRepository.existsById(imovelId)).thenReturn(false);

        assertThatThrownBy(() -> new ListarEstoqueImovelUseCase(imovelRepository, estoqueRepository, mapper)
                .execute(imovelId, null, null, PageRequest.of(0, 20))).isInstanceOf(ImovelNotFoundException.class);
        assertThatThrownBy(() -> new ListarAlertasEstoqueUseCase(imovelRepository, estoqueRepository, mapper)
                .execute(imovelId, PageRequest.of(0, 20))).isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(estoqueRepository);
    }
}
