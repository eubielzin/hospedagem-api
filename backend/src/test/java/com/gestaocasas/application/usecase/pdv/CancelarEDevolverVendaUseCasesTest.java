package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.VendaFixtures;
import com.gestaocasas.application.dto.CancelamentoVendaRequest;
import com.gestaocasas.application.dto.DevolucaoVendaRequest;
import com.gestaocasas.application.dto.ItemVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.Idempotencia;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.ItemVenda;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.VendaNotFoundException;
import com.gestaocasas.domain.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarEDevolverVendaUseCasesTest {

    private static final String CHAVE = "devolucao-0001";

    @Mock
    private VendaRepository vendaRepository;
    @Mock
    private MovimentadorEstoque movimentador;
    @Mock
    private Idempotencia idempotencia;

    private CancelarVendaUseCase cancelar;
    private DevolverItensVendaUseCase devolver;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID vendaId = UUID.randomUUID();
    private final UUID cocaId = UUID.randomUUID();
    private final UUID aguaId = UUID.randomUUID();
    private final UUID passeioId = UUID.randomUUID();
    private Venda venda;
    private ItemVenda itemCoca;

    @BeforeEach
    void setUp() {
        cancelar = new CancelarVendaUseCase(vendaRepository, movimentador, new VendaMapper());
        devolver = new DevolverItensVendaUseCase(vendaRepository, movimentador, idempotencia, new VendaMapper());
        Produto coca = VendaFixtures.produto(cocaId, "Coca-Cola", "6.00", true);
        Produto agua = VendaFixtures.produto(aguaId, "Água", "4.50", true);
        Produto passeio = VendaFixtures.produto(passeioId, "Passeio de Lancha", "350.00", false);
        venda = VendaFixtures.venda(vendaId, imovelId);
        itemCoca = VendaFixtures.item(venda, coca, 5, 2);
        VendaFixtures.item(venda, agua, 3, 3);
        VendaFixtures.item(venda, passeio, 1, 0);
        lenient().when(vendaRepository.findByIdForUpdate(vendaId)).thenReturn(Optional.of(venda));
        lenient().when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(idempotencia.iniciar(any(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    private static DevolucaoVendaRequest devolucao(UUID produtoId, int quantidade) {
        return new DevolucaoVendaRequest(List.of(new ItemVendaRequest(produtoId, quantidade)), "Cliente desistiu");
    }

    @Test
    void cancelamentoDevolveAoEstoqueSoOQueAindaNaoTinhaVoltado() {
        VendaResponse response = cancelar.execute(vendaId, new CancelamentoVendaRequest(" Lançada errada "));

        assertThat(response.status()).isEqualTo(StatusVenda.CANCELADA);
        assertThat(response.motivoCancelamento()).isEqualTo("Lançada errada");
        assertThat(response.canceladoEm()).isNotNull();
        // Coca: 5 vendidas, 2 já devolvidas. Água: tudo devolvido. Passeio: não tem estoque.
        verify(movimentador).movimentar(imovelId, cocaId, TipoMovimentacao.DEVOLUCAO, 3,
                "Cancelamento da venda: Lançada errada", TipoReferenciaMovimentacao.VENDA, vendaId);
        verify(movimentador, never()).movimentar(any(), eq(aguaId), any(), anyInt(), any(), any(), any());
        verify(movimentador, never()).movimentar(any(), eq(passeioId), any(), anyInt(), any(), any(), any());
    }

    @Test
    void cancelarVendaJaCanceladaNaoDevolveEstoqueDeNovo() {
        venda.cancelar("Primeiro cancelamento");

        VendaResponse response = cancelar.execute(vendaId, new CancelamentoVendaRequest("Segundo"));

        assertThat(response.motivoCancelamento()).isEqualTo("Primeiro cancelamento");
        verifyNoInteractions(movimentador);
        verify(vendaRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelarVendaInexistenteLancaExcecao() {
        UUID outra = UUID.randomUUID();
        when(vendaRepository.findByIdForUpdate(outra)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelar.execute(outra, new CancelamentoVendaRequest("x")))
                .isInstanceOf(VendaNotFoundException.class);
    }

    @Test
    void devolucaoParcialVoltaAoEstoqueDoImovelDaVenda() {
        ResultadoIdempotente<VendaResponse> resultado = devolver.execute(vendaId, devolucao(cocaId, 2), CHAVE);

        assertThat(resultado.repetida()).isFalse();
        assertThat(itemCoca.getQuantidadeDevolvida()).isEqualTo(4);
        assertThat(resultado.valor().status()).isEqualTo(StatusVenda.CONCLUIDA);
        // 4 Cocas a 6,00 + 3 Águas a 4,50.
        assertThat(resultado.valor().totalDevolvido()).isEqualByComparingTo("37.50");
        verify(movimentador).movimentar(imovelId, cocaId, TipoMovimentacao.DEVOLUCAO, 2,
                "Devolução da venda: Cliente desistiu", TipoReferenciaMovimentacao.VENDA, vendaId);
        verify(idempotencia).concluir(OperacaoIdempotente.DEVOLUCAO_VENDA, CHAVE, vendaId);
    }

    @Test
    void naoDevolveMaisDoQueFoiVendido() {
        assertThatThrownBy(() -> devolver.execute(vendaId, devolucao(cocaId, 4), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("vendido 5, já devolvido 2");
        assertThat(itemCoca.getQuantidadeDevolvida()).isEqualTo(2);
        verifyNoInteractions(movimentador);
    }

    @Test
    void recusaProdutoForaDaVendaItemSemEstoqueEVendaCancelada() {
        assertThatThrownBy(() -> devolver.execute(vendaId, devolucao(UUID.randomUUID(), 1), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("não faz parte");
        assertThatThrownBy(() -> devolver.execute(vendaId, devolucao(passeioId, 1), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("não é um produto físico");

        venda.cancelar("x");
        assertThatThrownBy(() -> devolver.execute(vendaId, devolucao(cocaId, 1), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("cancelada");
        verifyNoInteractions(movimentador);
    }

    @Test
    void devolucaoRepetidaComAMesmaChaveNaoDevolveDeNovo() {
        when(idempotencia.iniciar(eq(OperacaoIdempotente.DEVOLUCAO_VENDA), eq(CHAVE), anyString()))
                .thenReturn(Optional.of(vendaId));

        ResultadoIdempotente<VendaResponse> resultado = devolver.execute(vendaId, devolucao(cocaId, 2), CHAVE);

        assertThat(resultado.repetida()).isTrue();
        assertThat(itemCoca.getQuantidadeDevolvida()).isEqualTo(2);
        verifyNoInteractions(movimentador);
    }
}
