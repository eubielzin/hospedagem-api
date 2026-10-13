package com.gestaocasas.application.usecase.pdv;

import com.gestaocasas.ImovelFixtures;
import com.gestaocasas.VendaFixtures;
import com.gestaocasas.application.dto.ItemVendaRequest;
import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.application.dto.VendaRequest;
import com.gestaocasas.application.dto.VendaResponse;
import com.gestaocasas.application.mapper.VendaMapper;
import com.gestaocasas.application.service.Idempotencia;
import com.gestaocasas.application.service.MovimentadorEstoque;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.entity.Venda;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StatusPagamento;
import com.gestaocasas.domain.enums.StatusVenda;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.enums.TipoVenda;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import com.gestaocasas.domain.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarVendaUseCaseTest {

    private static final String CHAVE = "venda-0001";

    @Mock
    private VendaRepository vendaRepository;
    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private MovimentadorEstoque movimentador;
    @Mock
    private Idempotencia idempotencia;

    private RegistrarVendaUseCase useCase;

    private final UUID imovelId = UUID.randomUUID();
    private final UUID vendaId = UUID.randomUUID();
    private final UUID cocaId = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private final UUID aguaId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final UUID passeioId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
    private Produto coca;
    private Produto agua;
    private Produto passeio;

    @BeforeEach
    void setUp() {
        useCase = new RegistrarVendaUseCase(vendaRepository, imovelRepository, produtoRepository, movimentador,
                idempotencia, new VendaMapper());
        coca = VendaFixtures.produto(cocaId, "Coca-Cola", "6.00", true);
        agua = VendaFixtures.produto(aguaId, "Água", "4.50", true);
        passeio = VendaFixtures.produto(passeioId, "Passeio de Lancha", "350.00", false);
        lenient().when(idempotencia.iniciar(any(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(imovelRepository.findById(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.ATIVO)));
        lenient().when(produtoRepository.findAllById(any())).thenReturn(List.of(coca, agua, passeio));
        lenient().when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> {
            Venda venda = invocation.getArgument(0);
            venda.setId(vendaId);
            return venda;
        });
    }

    private VendaRequest request(BigDecimal totalEsperado, ItemVendaRequest... itens) {
        return new VendaRequest(imovelId, List.of(itens), "  Hóspede do quarto 2  ", totalEsperado);
    }

    private static ItemVendaRequest item(UUID produtoId, int quantidade) {
        return new ItemVendaRequest(produtoId, quantidade);
    }

    @Test
    void calculaOTotalComOsPrecosDoCadastroEGuardaAFotografiaDoProduto() {
        VendaResponse venda = useCase.execute(request(null, item(cocaId, 3), item(aguaId, 2), item(passeioId, 1)),
                CHAVE).valor();

        assertThat(venda.id()).isEqualTo(vendaId);
        assertThat(venda.imovelId()).isEqualTo(imovelId);
        assertThat(venda.total()).isEqualByComparingTo("377.00");
        assertThat(venda.tipo()).isEqualTo(TipoVenda.AVULSA);
        assertThat(venda.status()).isEqualTo(StatusVenda.CONCLUIDA);
        assertThat(venda.observacao()).isEqualTo("Hóspede do quarto 2");
        assertThat(venda.itens()).hasSize(3);
        assertThat(venda.itens().get(0).produtoNome()).isEqualTo("Coca-Cola");
        assertThat(venda.itens().get(0).precoUnitario()).isEqualByComparingTo("6.00");
        assertThat(venda.itens().get(0).subtotal()).isEqualByComparingTo("18.00");
    }

    @Test
    void vendaNascePendenteDePagamento() {
        VendaResponse venda = useCase.execute(request(null, item(cocaId, 1)), CHAVE).valor();

        assertThat(venda.pagamentoStatus()).isEqualTo(StatusPagamento.PENDENTE);
    }

    @Test
    void baixaOEstoqueSomenteDoImovelDaVendaEEmOrdemFixaDeProduto() {
        useCase.execute(request(null, item(cocaId, 3), item(passeioId, 1), item(aguaId, 2)), CHAVE);

        InOrder ordem = inOrder(movimentador);
        ordem.verify(movimentador).movimentar(imovelId, aguaId, TipoMovimentacao.SAIDA_VENDA, 2, "Venda",
                TipoReferenciaMovimentacao.VENDA, vendaId);
        ordem.verify(movimentador).movimentar(imovelId, cocaId, TipoMovimentacao.SAIDA_VENDA, 3, "Venda",
                TipoReferenciaMovimentacao.VENDA, vendaId);
        // Passeio não controla estoque: nenhuma baixa.
        verify(movimentador, never()).movimentar(any(), eq(passeioId), any(), anyInt(), any(), any(), any());
    }

    @Test
    void semSaldoAVendaNaoEhConcluidaNemOcupaAChave() {
        when(movimentador.movimentar(any(), eq(cocaId), any(), anyInt(), any(), any(), any()))
                .thenThrow(new EstoqueInsuficienteException("Coca-Cola", 1, 3));

        assertThatThrownBy(() -> useCase.execute(request(null, item(cocaId, 3)), CHAVE))
                .isInstanceOf(EstoqueInsuficienteException.class);
        verify(idempotencia, never()).concluir(any(), any(), any());
    }

    @Test
    void totalEsperadoIgualEAceitoEDiferenteERecusadoAntesDeBaixarEstoque() {
        assertThat(useCase.execute(request(new BigDecimal("18.0"), item(cocaId, 3)), CHAVE).valor().total())
                .isEqualByComparingTo("18.00");

        assertThatThrownBy(() -> useCase.execute(request(new BigDecimal("15.00"), item(cocaId, 3)), "venda-0002"))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("18.00").hasMessageContaining("15.00");
        // Só a primeira venda foi gravada e baixou estoque; a divergente parou antes disso.
        verify(vendaRepository, times(1)).save(any());
        verify(movimentador, times(1)).movimentar(any(), any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void requisicaoRepetidaDevolveAVendaJaRegistradaSemBaixarEstoque() {
        Venda registrada = VendaFixtures.venda(vendaId, imovelId);
        VendaFixtures.item(registrada, coca, 3, 0);
        when(idempotencia.iniciar(eq(OperacaoIdempotente.VENDA), eq(CHAVE), anyString()))
                .thenReturn(Optional.of(vendaId));
        when(vendaRepository.findById(vendaId)).thenReturn(Optional.of(registrada));

        ResultadoIdempotente<VendaResponse> resultado = useCase.execute(request(null, item(cocaId, 3)), CHAVE);

        assertThat(resultado.repetida()).isTrue();
        assertThat(resultado.valor().id()).isEqualTo(vendaId);
        verifyNoInteractions(movimentador, produtoRepository);
        verify(vendaRepository, never()).save(any());
    }

    @Test
    void registraOResultadoNaChaveAoConcluir() {
        assertThat(useCase.execute(request(null, item(cocaId, 1)), CHAVE).repetida()).isFalse();

        verify(idempotencia).concluir(OperacaoIdempotente.VENDA, CHAVE, vendaId);
    }

    @Test
    void recusaProdutoRepetidoInexistenteOuInativo() {
        assertThatThrownBy(() -> useCase.execute(request(null, item(cocaId, 1), item(cocaId, 2)), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("mais de uma vez");

        UUID desconhecido = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.execute(request(null, item(desconhecido, 1)), CHAVE))
                .isInstanceOf(ProdutoNotFoundException.class);

        coca.setAtivo(false);
        assertThatThrownBy(() -> useCase.execute(request(null, item(cocaId, 1)), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("inativo");
        verifyNoInteractions(movimentador);
        verify(vendaRepository, never()).save(any());
    }

    @Test
    void recusaImovelInexistenteOuInativo() {
        when(imovelRepository.findById(imovelId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(request(null, item(cocaId, 1)), CHAVE))
                .isInstanceOf(ImovelNotFoundException.class);

        when(imovelRepository.findById(imovelId))
                .thenReturn(Optional.of(ImovelFixtures.imovel(imovelId, StatusImovel.INATIVO)));
        assertThatThrownBy(() -> useCase.execute(request(null, item(cocaId, 1)), CHAVE))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("inativo");
        verifyNoInteractions(movimentador);
    }
}
