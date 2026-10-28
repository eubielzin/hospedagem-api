package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Tarifa;
import com.gestaocasas.domain.exception.CampoInvalidoException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.TarifaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CotadorReservaTest {

    private static final LocalDate DIA = LocalDate.of(2027, 1, 10);

    @Mock
    private TarifaRepository repository;

    private CotadorReserva cotador;
    private Imovel uh;
    private final List<Tarifa> tarifas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cotador = new CotadorReserva(repository);
        CategoriaUh categoria = new CategoriaUh();
        categoria.setId(UUID.randomUUID());
        uh = new Imovel();
        uh.setNome("Casa Flor");
        uh.setCategoria(categoria);
        lenient().when(repository.findDaCategoriaNoPeriodo(eq(categoria.getId()), any(), any())).thenReturn(tarifas);
        tarifa("Tarifa base", DIA.minusDays(30), DIA.plusDays(300), "380.00", t -> { });
    }

    private void tarifa(String nome, LocalDate inicio, LocalDate fim, String valor, Consumer<Tarifa> ajuste) {
        Tarifa tarifa = new Tarifa();
        tarifa.setNome(nome);
        tarifa.setInicio(inicio);
        tarifa.setFim(fim);
        tarifa.setValorDiaria(new BigDecimal(valor));
        tarifa.setEstadiaMinima(1);
        ajuste.accept(tarifa);
        tarifas.add(tarifa);
    }

    @Test
    void cadaNoiteUsaATarifaDePeriodoMaisCurto() {
        tarifa("Feriado", DIA.plusDays(1), DIA.plusDays(1), "500.00", t -> { });

        CotacaoResponse cotacao = cotador.cotar(uh, DIA, DIA.plusDays(3));

        assertThat(cotacao.noites()).isEqualTo(3);
        assertThat(cotacao.valorTotal()).isEqualByComparingTo("1260.00");
        assertThat(cotacao.diarias()).extracting(CotacaoResponse.Diaria::tarifa)
                .containsExactly("Tarifa base", "Feriado", "Tarifa base");
        assertThat(cotacao.diarias()).extracting(CotacaoResponse.Diaria::data)
                .containsExactly(DIA, DIA.plusDays(1), DIA.plusDays(2));
    }

    @Test
    void semTarifaNaNoiteOuSemCategoria() {
        assertThatThrownBy(() -> cotador.cotar(uh, DIA.plusDays(301), DIA.plusDays(303)))
                .hasMessage("Não há tarifa cadastrada para Casa Flor em 07/11/2027.")
                .extracting("codigo").isEqualTo("RF-31");

        uh.setCategoria(null);
        assertThatThrownBy(() -> cotador.cotar(uh, DIA, DIA.plusDays(1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting("codigo").isEqualTo("RF-31");
    }

    @Test
    void restricoesDeVenda() {
        tarifa("Fechada", DIA.plusDays(5), DIA.plusDays(5), "380.00", t -> t.setVendaFechada(true));
        tarifa("Sem chegada", DIA.plusDays(10), DIA.plusDays(10), "380.00", t -> t.setFechadoChegada(true));
        tarifa("Sem saída", DIA.plusDays(20), DIA.plusDays(20), "380.00", t -> t.setFechadoSaida(true));
        tarifa("Mínimo", DIA.plusDays(30), DIA.plusDays(31), "380.00", t -> t.setEstadiaMinima(3));

        assertThatThrownBy(() -> cotador.cotar(uh, DIA.plusDays(4), DIA.plusDays(7)))
                .hasMessage("Venda fechada em 15/01/2027 (Fechada).").extracting("codigo").isEqualTo("RF-32");
        assertThatThrownBy(() -> cotador.cotar(uh, DIA.plusDays(10), DIA.plusDays(12)))
                .hasMessage("Período fechado para chegada (Sem chegada).");
        // A saída fechada vale no dia do checkout, que não é uma noite da estadia.
        assertThatThrownBy(() -> cotador.cotar(uh, DIA.plusDays(18), DIA.plusDays(20)))
                .hasMessage("Período fechado para saída (Sem saída).");
        assertThat(cotador.cotar(uh, DIA.plusDays(19), DIA.plusDays(21)).noites()).isEqualTo(2);
        assertThatThrownBy(() -> cotador.cotar(uh, DIA.plusDays(29), DIA.plusDays(31)))
                .hasMessage("Estadia mínima de 3 noites no período.");
        assertThat(cotador.cotar(uh, DIA.plusDays(29), DIA.plusDays(32)).noites()).isEqualTo(3);
    }

    @Test
    void saidaAntesOuNoDiaDaEntradaE400() {
        assertThatThrownBy(() -> cotador.cotar(uh, DIA, DIA))
                .isInstanceOf(CampoInvalidoException.class)
                .hasMessage("A saída deve ser depois da entrada.");
    }
}
