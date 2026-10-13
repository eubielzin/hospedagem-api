package com.gestaocasas.application.usecase.produto;

import com.gestaocasas.application.dto.ProdutoBuscaResponse;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ProdutoBuscaView;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarProdutosUseCaseTest {

    private static final int LIMITE_MAXIMO = 50;

    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ImovelRepository imovelRepository;

    private BuscarProdutosUseCase useCase;

    private final UUID imovelId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new BuscarProdutosUseCase(produtoRepository, imovelRepository, LIMITE_MAXIMO);
    }

    private static ProdutoBuscaView linha(String nome, boolean controlaEstoque, Integer quantidade) {
        UUID id = UUID.randomUUID();
        return new ProdutoBuscaView() {
            public UUID getId() { return id; }
            public String getCodigo() { return "COD"; }
            public String getCodigoBarras() { return null; }
            public String getNome() { return nome; }
            public String getCategoria() { return controlaEstoque ? "CONSUMO" : "TAXA"; }
            public String getUnidade() { return "UN"; }
            public BigDecimal getPrecoVenda() { return new BigDecimal("6.00"); }
            public Boolean getControlaEstoque() { return controlaEstoque; }
            public Boolean getAtivo() { return true; }
            public Integer getQuantidadeDisponivel() { return quantidade; }
        };
    }

    @Test
    void devolveSaldoDoImovelEMarcaProdutoFisicoZerado() {
        when(imovelRepository.existsById(imovelId)).thenReturn(true);
        when(produtoRepository.buscar(anyString(), any(), anyBoolean(), anyBoolean(), anyInt())).thenReturn(List.of(
                linha("Coca-Cola", true, 20), linha("Coca-Cola Zero", true, 0), linha("Taxa", false, null)));

        List<ProdutoBuscaResponse> resultado = useCase.execute("c", imovelId, true, 10);

        assertThat(resultado).extracting(ProdutoBuscaResponse::quantidadeDisponivel).containsExactly(20, 0, null);
        assertThat(resultado).extracting(ProdutoBuscaResponse::semEstoque).containsExactly(false, true, false);
        assertThat(resultado.get(0).precoVenda()).isEqualByComparingTo("6.00");
        assertThat(resultado.get(2).categoria()).isEqualTo(CategoriaProduto.TAXA);
        verify(produtoRepository).buscar("c", imovelId, true, true, 10);
    }

    @Test
    void semImovelNaoPedeSaldoDeNenhumaCasa() {
        when(produtoRepository.buscar(anyString(), any(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(List.of(linha("Coca-Cola", true, null)));

        List<ProdutoBuscaResponse> resultado = useCase.execute("coca", null, false, 10);

        assertThat(resultado.get(0).quantidadeDisponivel()).isNull();
        assertThat(resultado.get(0).semEstoque()).isFalse();
        verify(produtoRepository).buscar("coca", null, false, false, 10);
        verifyNoInteractions(imovelRepository);
    }

    @Test
    void reduzOLimiteAoMaximoConfigurado() {
        useCase.execute("a", null, true, 5000);
        verify(produtoRepository).buscar("a", null, false, true, LIMITE_MAXIMO);

        useCase.execute("b", null, true, 0);
        verify(produtoRepository).buscar("b", null, false, true, 1);
    }

    @Test
    void aparaOTermoEEscapaCuringas() {
        useCase.execute("  50%_off!  ", null, true, 10);

        verify(produtoRepository).buscar(eq("50!%!_off!!"), any(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    void recusaTermoVazioEImovelInexistente() {
        assertThatThrownBy(() -> useCase.execute("   ", null, true, 10)).isInstanceOf(IllegalArgumentException.class);

        when(imovelRepository.existsById(imovelId)).thenReturn(false);
        assertThatThrownBy(() -> useCase.execute("c", imovelId, true, 10)).isInstanceOf(ImovelNotFoundException.class);
        verifyNoInteractions(produtoRepository);
    }
}
