package com.gestaocasas.application.service;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.ProdutoFixtures;
import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.MovimentacaoEstoqueRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimentadorEstoqueTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private EstoqueImovelRepository estoqueRepository;
    @Mock
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    private MovimentadorEstoque movimentador;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID produtoId = UUID.randomUUID();
    private Produto produto;
    private EstoqueImovel estoque;

    @BeforeEach
    void setUp() {
        movimentador = new MovimentadorEstoque(imovelRepository, produtoRepository, estoqueRepository,
                movimentacaoRepository);
        produto = ProdutoFixtures.produto(produtoId, true);
        estoque = new EstoqueImovel();
        estoque.setImovel(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO));
        estoque.setProduto(produto);
        lenient().when(imovelRepository.existsById(imovelId)).thenReturn(true);
        lenient().when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produto));
        lenient().when(estoqueRepository.findForUpdate(imovelId, produtoId)).thenReturn(Optional.of(estoque));
        lenient().when(movimentacaoRepository.saveAndFlush(any(MovimentacaoEstoque.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MovimentacaoEstoque movimentar(TipoMovimentacao tipo, int quantidade) {
        return movimentador.movimentar(imovelId, produtoId, tipo, quantidade, " motivo ", null, null);
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimentacao.class,
            names = {"ENTRADA", "AJUSTE_POSITIVO", "DEVOLUCAO", "TRANSFERENCIA_ENTRADA"})
    void tiposDeEntradaSomamAoSaldo(TipoMovimentacao tipo) {
        estoque.setQuantidadeAtual(10);

        MovimentacaoEstoque movimentacao = movimentar(tipo, 4);

        assertThat(estoque.getQuantidadeAtual()).isEqualTo(14);
        assertThat(movimentacao.getSaldoAnterior()).isEqualTo(10);
        assertThat(movimentacao.getSaldoPosterior()).isEqualTo(14);
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimentacao.class,
            names = {"SAIDA_VENDA", "PERDA", "AJUSTE_NEGATIVO", "TRANSFERENCIA_SAIDA"})
    void tiposDeSaidaSubtraemDoSaldo(TipoMovimentacao tipo) {
        estoque.setQuantidadeAtual(10);

        MovimentacaoEstoque movimentacao = movimentar(tipo, 4);

        assertThat(estoque.getQuantidadeAtual()).isEqualTo(6);
        assertThat(movimentacao.getSaldoAnterior()).isEqualTo(10);
        assertThat(movimentacao.getSaldoPosterior()).isEqualTo(6);
    }

    @Test
    void registraProdutoImovelTipoMotivoEReferencia() {
        UUID referencia = UUID.randomUUID();

        MovimentacaoEstoque movimentacao = movimentador.movimentar(imovelId, produtoId, TipoMovimentacao.ENTRADA,
                3, "  Compra no mercado  ", TipoReferenciaMovimentacao.TRANSFERENCIA, referencia);

        assertThat(movimentacao.getImovel().getId()).isEqualTo(imovelId);
        assertThat(movimentacao.getProduto()).isSameAs(produto);
        assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(movimentacao.getQuantidade()).isEqualTo(3);
        assertThat(movimentacao.getMotivo()).isEqualTo("Compra no mercado");
        assertThat(movimentacao.getReferenciaTipo()).isEqualTo(TipoReferenciaMovimentacao.TRANSFERENCIA);
        assertThat(movimentacao.getReferenciaId()).isEqualTo(referencia);
    }

    @Test
    void bloqueiaOSaldoAntesDeLerAQuantidade() {
        movimentar(TipoMovimentacao.ENTRADA, 1);

        InOrder ordem = inOrder(estoqueRepository, movimentacaoRepository);
        ordem.verify(estoqueRepository).criarSeAusente(imovelId, produtoId);
        ordem.verify(estoqueRepository).findForUpdate(imovelId, produtoId);
        ordem.verify(movimentacaoRepository).saveAndFlush(any());
    }

    @Test
    void permiteZerarOSaldo() {
        estoque.setQuantidadeAtual(4);

        assertThat(movimentar(TipoMovimentacao.SAIDA_VENDA, 4).getSaldoPosterior()).isZero();
    }

    @Test
    void recusaSaidaMaiorQueOSaldoSemAlterarNada() {
        estoque.setQuantidadeAtual(3);

        assertThatThrownBy(() -> movimentar(TipoMovimentacao.SAIDA_VENDA, 5))
                .isInstanceOf(EstoqueInsuficienteException.class)
                .hasMessageContaining("disponível 3, solicitado 5");
        assertThat(estoque.getQuantidadeAtual()).isEqualTo(3);
        verify(movimentacaoRepository, never()).saveAndFlush(any());
    }

    @Test
    void recusaSaldoAcimaDoLimite() {
        estoque.setQuantidadeAtual(MovimentadorEstoque.SALDO_MAXIMO);

        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 1))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(estoque.getQuantidadeAtual()).isEqualTo(MovimentadorEstoque.SALDO_MAXIMO);
    }

    @Test
    void recusaProdutoQueNaoControlaEstoque() {
        produto.setControlaEstoque(false);

        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 1))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não controla estoque");
        verifyNoInteractions(estoqueRepository, movimentacaoRepository);
    }

    @Test
    void produtoInativoNaoRecebeEntradaMasPodeSerBaixado() {
        produto.setAtivo(false);
        estoque.setQuantidadeAtual(5);

        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 1))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("inativo");
        assertThat(movimentar(TipoMovimentacao.PERDA, 5).getSaldoPosterior()).isZero();
    }

    @Test
    void recusaQuantidadeNaoPositivaEImovelOuProdutoInexistente() {
        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 0))
                .isInstanceOf(IllegalArgumentException.class);

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 1))
                .isInstanceOf(ProdutoNotFoundException.class);

        when(imovelRepository.existsById(imovelId)).thenReturn(false);
        assertThatThrownBy(() -> movimentar(TipoMovimentacao.ENTRADA, 1))
                .isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(estoqueRepository, movimentacaoRepository);
    }
}
