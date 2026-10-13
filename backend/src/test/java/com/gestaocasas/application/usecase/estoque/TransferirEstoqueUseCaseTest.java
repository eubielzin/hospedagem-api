package com.gestaocasas.application.usecase.estoque;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.application.dto.TransferenciaEstoqueRequest;
import com.gestaocasas.application.dto.TransferenciaEstoqueResponse;
import com.gestaocasas.application.mapper.EstoqueMapper;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferirEstoqueUseCaseTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MovimentadorEstoque movimentador;

    private TransferirEstoqueUseCase useCase;

    private final UUID menor = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID maior = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID produtoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new TransferirEstoqueUseCase(imovelRepository, produtoRepository, movimentador, new EstoqueMapper());
        lenient().when(imovelRepository.existsById(any())).thenReturn(true);
        lenient().when(produtoRepository.existsById(produtoId)).thenReturn(true);
        lenient().when(movimentador.movimentar(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
                    movimentacao.setImovel(ImovelFixtures.imovel(invocation.getArgument(0), StatusImovel.ATIVO));
                    movimentacao.setProduto(ProdutoFixtures.produto(invocation.getArgument(1), true));
                    movimentacao.setTipo(invocation.getArgument(2));
                    movimentacao.setQuantidade(invocation.getArgument(3));
                    movimentacao.setReferenciaTipo(invocation.getArgument(5));
                    movimentacao.setReferenciaId(invocation.getArgument(6));
                    return movimentacao;
                });
    }

    private TransferenciaEstoqueRequest request(UUID origem, UUID destino) {
        return new TransferenciaEstoqueRequest(origem, destino, produtoId, 7, "Reposição");
    }

    @Test
    void geraSaidaNaOrigemEEntradaNoDestinoComAMesmaReferencia() {
        TransferenciaEstoqueResponse response = useCase.execute(request(menor, maior));

        assertThat(response.saida().imovelId()).isEqualTo(menor);
        assertThat(response.saida().tipo()).isEqualTo(TipoMovimentacao.TRANSFERENCIA_SAIDA);
        assertThat(response.entrada().imovelId()).isEqualTo(maior);
        assertThat(response.entrada().tipo()).isEqualTo(TipoMovimentacao.TRANSFERENCIA_ENTRADA);
        assertThat(response.saida().quantidade()).isEqualTo(7);
        assertThat(response.entrada().quantidade()).isEqualTo(7);
        assertThat(response.transferenciaId()).isNotNull()
                .isEqualTo(response.saida().referenciaId())
                .isEqualTo(response.entrada().referenciaId());
        assertThat(response.saida().referenciaTipo()).isEqualTo(TipoReferenciaMovimentacao.TRANSFERENCIA);
    }

    @Test
    void bloqueiaOsSaldosSempreNaMesmaOrdemEBaixaAOrigemPrimeiro() {
        useCase.execute(request(maior, menor));

        InOrder ordem = inOrder(movimentador);
        ordem.verify(movimentador).travar(menor, produtoId);
        ordem.verify(movimentador).travar(maior, produtoId);
        ordem.verify(movimentador).movimentar(eq(maior), eq(produtoId), eq(TipoMovimentacao.TRANSFERENCIA_SAIDA),
                eq(7), any(), any(), any());
        ordem.verify(movimentador).movimentar(eq(menor), eq(produtoId), eq(TipoMovimentacao.TRANSFERENCIA_ENTRADA),
                eq(7), any(), any(), any());
    }

    @Test
    void semSaldoNaOrigemNaoHaEntradaNoDestino() {
        when(movimentador.movimentar(eq(menor), any(), eq(TipoMovimentacao.TRANSFERENCIA_SAIDA), anyInt(), any(),
                any(), any())).thenThrow(new EstoqueInsuficienteException("Coca", 3, 7));

        assertThatThrownBy(() -> useCase.execute(request(menor, maior)))
                .isInstanceOf(EstoqueInsuficienteException.class);
        verify(movimentador, never()).movimentar(eq(maior), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void recusaOrigemIgualAoDestino() {
        assertThatThrownBy(() -> useCase.execute(request(menor, menor)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("imóveis diferentes");
        verifyNoInteractions(movimentador);
    }

    @Test
    void recusaImovelOuProdutoInexistenteAntesDeBloquearQualquerSaldo() {
        when(imovelRepository.existsById(maior)).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute(request(menor, maior)))
                .isInstanceOf(ImovelNotFoundException.class)
                .hasMessageContaining(maior.toString());

        when(imovelRepository.existsById(maior)).thenReturn(true);
        when(produtoRepository.existsById(produtoId)).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute(request(menor, maior)))
                .isInstanceOf(ProdutoNotFoundException.class);
        verifyNoInteractions(movimentador);
    }
}
