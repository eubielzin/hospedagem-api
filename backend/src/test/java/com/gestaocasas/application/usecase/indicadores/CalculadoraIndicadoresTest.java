package com.gestaocasas.application.usecase.indicadores;

import com.gestaocasas.application.dto.IndicadoresResponse;
import com.gestaocasas.application.dto.IndicadoresResponse.Contagem;
import com.gestaocasas.application.dto.PrevisaoResponse;
import com.gestaocasas.application.usecase.indicadores.CalculadoraIndicadores.Consumo;
import com.gestaocasas.application.usecase.indicadores.CalculadoraIndicadores.Dados;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Despesa;
import com.gestaocasas.domain.entity.Encerramento;
import com.gestaocasas.domain.entity.Fnrh;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.enums.FormaPagamento;
import com.gestaocasas.domain.enums.MeioTransporte;
import com.gestaocasas.domain.enums.MotivoViagem;
import com.gestaocasas.domain.enums.RegimePensao;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoUh;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cenário de 2 casas e o imóvel completo em março/2027 (10 dias), com os números conferidos à mão:
 * R1 casa A, OTA 10%, 01–04/03, 900; R2 imóvel completo, direto, 09–12/03, 3000; R3 casa B cancelada com 50 retidos;
 * R4 casa B, 25/02–02/03, 500 (1 noite no período).
 */
class CalculadoraIndicadoresTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate INICIO = LocalDate.of(2027, 3, 1);
    private static final LocalDate FIM = LocalDate.of(2027, 3, 10);

    private Imovel casaA;
    private Imovel casaB;
    private Canal ota;
    private Canal direto;
    private Hospede brasileira;
    private Hospede portuguesa;
    private CalculadoraIndicadores calculadora;

    @BeforeEach
    void setUp() {
        casaA = uh("Casa A", TipoUh.CASA);
        casaB = uh("Casa B", TipoUh.CASA);
        Imovel completo = uh("Imóvel completo", TipoUh.IMOVEL_COMPLETO);
        ota = canal("Booking.com", "10");
        direto = canal("Direto", "0");
        brasileira = hospede("Ana", "Brasileira", null);
        portuguesa = hospede("Rita", "Portuguesa", "Lisboa/Portugal");

        Reserva r1 = reserva(casaA, ota, brasileira, "2027-03-01", "2027-03-04", "900", StatusReserva.HOSPEDADO,
                "2027-02-20", 2, 0, RegimePensao.CAFE_DA_MANHA);
        Fnrh fnrh = new Fnrh();
        fnrh.setCidadeProcedencia("São Luís/MA");
        fnrh.setCidadeDestino("Barreirinhas/MA");
        fnrh.setMotivoViagem(MotivoViagem.LAZER);
        fnrh.setMeioTransporte(MeioTransporte.CARRO);
        r1.setFnrh(fnrh);
        Reserva r2 = reserva(completo, direto, portuguesa, "2027-03-09", "2027-03-12", "3000", StatusReserva.RESERVADO,
                "2027-03-09", 4, 2, RegimePensao.SEM_REFEICOES);
        Reserva r3 = reserva(casaB, direto, brasileira, "2027-03-05", "2027-03-06", "100", StatusReserva.CANCELADA,
                "2027-03-01", 1, 0, RegimePensao.SEM_REFEICOES);
        Encerramento encerramento = new Encerramento();
        encerramento.setData(LocalDate.of(2027, 3, 2));
        encerramento.setValorRetido(new BigDecimal("50.00"));
        r3.setEncerramento(encerramento);
        Reserva r4 = reserva(casaB, direto, brasileira, "2027-02-25", "2027-03-02", "500",
                StatusReserva.CHECKOUT_REALIZADO, "2027-02-01", 2, 0, RegimePensao.SEM_REFEICOES);

        List<Consumo> consumos = List.of(
                new Consumo(LocalDate.of(2027, 3, 2), r1.getId(), "Água", CategoriaProduto.CONSUMO, 3, 15),
                new Consumo(LocalDate.of(2027, 3, 3), null, "Suco", CategoriaProduto.CONSUMO, 2, 16));
        List<Pagamento> pagamentos = List.of(pagamento("2027-03-03", FormaPagamento.CREDITO, "200", "6"),
                pagamento("2027-03-05", FormaPagamento.PIX, "100", "0"));
        List<Despesa> despesas = List.of(despesa("Energia", "2027-03-05", "300"), despesa("Água", "2027-03-20", "100"));
        Map<UUID, LocalDate> primeira = Map.of(brasileira.getId(), LocalDate.of(2027, 2, 25),
                portuguesa.getId(), LocalDate.of(2027, 3, 9));
        calculadora = new CalculadoraIndicadores(new Dados(List.of(casaA, casaB), List.of(ota, direto),
                List.of(r1, r2, r3, r4), primeira, consumos, pagamentos, despesas,
                r -> LocalDate.ofInstant(r.getCriadoEm(), BRASILIA)));
    }

    @Test
    void resumoDoPeriodoEDoAnterior() {
        IndicadoresResponse.Resumo resumo = calculadora.indicadores(INICIO, FIM).resumo();

        // R1 3 noites + completo 2 noites × 2 casas + R4 1 noite = 8 de 20 disponíveis.
        assertThat(resumo.noitesVendidas()).isEqualTo(8);
        assertThat(resumo.ocupacao()).isEqualTo(0.4);
        assertThat(resumo.receitaHospedagem()).isEqualByComparingTo("3000.00");
        assertThat(resumo.adr()).isEqualByComparingTo("375.00");
        assertThat(resumo.revpar()).isEqualByComparingTo("150.00");
        assertThat(resumo.comissoes()).isEqualByComparingTo("90.00");
        assertThat(resumo.receitaConsumo()).isEqualByComparingTo("31.00");
        assertThat(resumo.receitaRetida()).isEqualByComparingTo("50.00");
        assertThat(resumo.taxasCartao()).isEqualByComparingTo("6.00");
        assertThat(resumo.despesas()).isEqualByComparingTo("300.00");
        assertThat(resumo.resultado()).isEqualByComparingTo("2685.00");
        assertThat(resumo.reservas()).isEqualTo(2);
        assertThat(resumo.hospedes()).isEqualTo(8);
        assertThat(resumo.estadiaMedia()).isEqualTo(3.0);
        assertThat(resumo.antecedenciaMedia()).isEqualTo(4.5);
        assertThat(resumo.cancelamentos()).isEqualTo(1);
        assertThat(resumo.taxaCancelamento()).isEqualTo(0.3333);
        assertThat(resumo.recorrencia()).isEqualTo(0.5);

        IndicadoresResponse.Resumo anterior = calculadora.indicadores(INICIO, FIM).anterior();
        assertThat(anterior.noitesVendidas()).isEqualTo(4);
        assertThat(anterior.receitaHospedagem()).isEqualByComparingTo("400.00");
        assertThat(anterior.reservas()).isEqualTo(1);
    }

    @Test
    void quebrasPorCanalUhEPerfil() {
        IndicadoresResponse indicadores = calculadora.indicadores(INICIO, FIM);

        assertThat(indicadores.canais()).extracting(IndicadoresResponse.Canal::nome).containsExactly("Direto", "Booking.com");
        assertThat(indicadores.canais().get(0)).satisfies(c -> {
            assertThat(c.noites()).isEqualTo(5);
            assertThat(c.receita()).isEqualByComparingTo("2100.00");
            assertThat(c.cancelamentos()).isEqualTo(1);
        });
        assertThat(indicadores.canais().get(1).liquido()).isEqualByComparingTo("810.00");

        // A diária do completo é dividida entre as casas.
        assertThat(indicadores.uhs()).extracting(IndicadoresResponse.Uh::nome).containsExactly("Casa A", "Casa B");
        assertThat(indicadores.uhs().get(0)).satisfies(u -> {
            assertThat(u.noites()).isEqualTo(5);
            assertThat(u.receita()).isEqualByComparingTo("1900.00");
            assertThat(u.ocupacao()).isEqualTo(0.5);
            assertThat(u.consumo()).isEqualByComparingTo("15.00");
        });
        assertThat(indicadores.uhs().get(1).adr()).isEqualByComparingTo("366.67");

        assertThat(valores(indicadores.antecedencia())).containsExactly(1, 0, 1, 0, 0);
        assertThat(valores(indicadores.duracao())).containsExactly(0, 0, 2, 0, 0);
        assertThat(indicadores.composicao()).extracting(Contagem::rotulo)
                .containsExactlyInAnyOrder("Casal ou dupla", "Família com crianças");
        assertThat(indicadores.origemEstados()).extracting(Contagem::rotulo).containsExactly("MA");
        assertThat(indicadores.origemPaises()).extracting(Contagem::rotulo).containsExactlyInAnyOrder("Brasil", "Portugal");
        assertThat(indicadores.motivos()).extracting(Contagem::rotulo).containsExactly("Lazer/férias");
        assertThat(indicadores.transportes()).extracting(Contagem::rotulo).containsExactly("Automóvel");
        assertThat(indicadores.formasPagamento()).first().satisfies(f -> {
            assertThat(f.rotulo()).isEqualTo("Cartão de crédito");
            assertThat(f.taxas()).isEqualByComparingTo("6.00");
        });
        assertThat(indicadores.despesasPorCategoria()).extracting(Contagem::rotulo).containsExactly("Energia");
        assertThat(indicadores.produtos()).extracting(IndicadoresResponse.Produto::nome).containsExactly("Suco", "Água");
        assertThat(indicadores.clientes()).extracting(IndicadoresResponse.Cliente::nome).containsExactly("Rita", "Ana");
        assertThat(indicadores.clientes().get(0).origem()).isEqualTo("Lisboa/Portugal");
        assertThat(indicadores.mensal()).extracting(IndicadoresResponse.Mensal::mes).containsExactly("mar/27");
        assertThat(indicadores.mensal().getFirst().receitaConsumo()).isEqualByComparingTo("81.00");
        assertThat(indicadores.diasSemana()).hasSize(7).first().extracting(IndicadoresResponse.DiaSemana::dia)
                .isEqualTo("seg");
        assertThat(indicadores.ocupacaoDiaria()).hasSize(10);
    }

    @Test
    void previsaoOnTheBooks() {
        PrevisaoResponse previsao = calculadora.previsao(LocalDate.of(2027, 3, 9), 7, 3, 5);

        assertThat(previsao.noitesVendidas()).isEqualTo(6);
        assertThat(previsao.receita()).isEqualByComparingTo("3000.00");
        assertThat(previsao.dias()).hasSize(7);
        assertThat(previsao.dias().getFirst().chegadas()).isEqualTo(1);
        assertThat(previsao.dias().getFirst().ocupacao()).isEqualTo(1.0);
        assertThat(previsao.semanas()).singleElement().satisfies(s -> assertThat(s.ocupacao()).isCloseTo(3.0 / 7,
                org.assertj.core.data.Offset.offset(0.0001)));
        assertThat(previsao.vendidas7()).isEqualTo(3);
    }

    private static List<Integer> valores(List<Contagem> contagens) {
        return contagens.stream().map(c -> c.valor().intValue()).toList();
    }

    private static Imovel uh(String nome, TipoUh tipo) {
        Imovel imovel = new Imovel();
        imovel.setId(UUID.randomUUID());
        imovel.setNome(nome);
        imovel.setTipo(tipo);
        return imovel;
    }

    private static Canal canal(String nome, String comissao) {
        Canal canal = new Canal();
        canal.setId(UUID.randomUUID());
        canal.setNome(nome);
        canal.setComissao(new BigDecimal(comissao));
        canal.setCor("#000000");
        return canal;
    }

    private static Hospede hospede(String nome, String nacionalidade, String endereco) {
        Hospede hospede = new Hospede();
        hospede.setId(UUID.randomUUID());
        hospede.setNome(nome);
        hospede.setNacionalidade(nacionalidade);
        hospede.setEndereco(endereco);
        return hospede;
    }

    private static Reserva reserva(Imovel uh, Canal canal, Hospede titular, String checkin, String checkout,
                                   String valor, StatusReserva status, String criadaEm, int adultos, int criancas,
                                   RegimePensao regime) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setImovel(uh);
        reserva.setCanal(canal);
        reserva.setComissaoCanal(canal.getComissao());
        reserva.setTitular(titular);
        reserva.setCheckin(LocalDate.parse(checkin));
        reserva.setCheckout(LocalDate.parse(checkout));
        reserva.setValorTotal(new BigDecimal(valor));
        reserva.setStatus(status);
        reserva.setCriadoEm(LocalDate.parse(criadaEm).atTime(10, 0).atZone(BRASILIA).toInstant());
        reserva.setAdultos(adultos);
        reserva.setCriancas(criancas);
        reserva.setRegimePensao(regime);
        return reserva;
    }

    private static Pagamento pagamento(String data, FormaPagamento forma, String bruto, String taxa) {
        Pagamento pagamento = new Pagamento();
        pagamento.setData(LocalDate.parse(data));
        pagamento.setForma(forma);
        pagamento.setValorBruto(new BigDecimal(bruto));
        pagamento.setTaxa(new BigDecimal(taxa));
        return pagamento;
    }

    private static Despesa despesa(String categoria, String vencimento, String valor) {
        Despesa despesa = new Despesa();
        despesa.setCategoria(categoria);
        despesa.setVencimento(LocalDate.parse(vencimento));
        despesa.setValor(new BigDecimal(valor));
        return despesa;
    }
}
