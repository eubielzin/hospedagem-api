package com.gestaocasas.application.usecase.indicadores;

import com.gestaocasas.application.dto.IndicadoresResponse;
import com.gestaocasas.application.dto.IndicadoresResponse.Cliente;
import com.gestaocasas.application.dto.IndicadoresResponse.Contagem;
import com.gestaocasas.application.dto.IndicadoresResponse.DiaSemana;
import com.gestaocasas.application.dto.IndicadoresResponse.Mensal;
import com.gestaocasas.application.dto.IndicadoresResponse.OcupacaoDiaria;
import com.gestaocasas.application.dto.IndicadoresResponse.Resumo;
import com.gestaocasas.application.dto.PrevisaoResponse;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Despesa;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.CategoriaProduto;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoUh;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Fórmulas dos indicadores (módulo 8) sobre um retrato dos dados do período; a mesma implementação de referência
 * do protótipo do front. Os valores são acumulados em double, como num relatório, e arredondados a 2 casas só na
 * saída: nenhum deles é gravado nem volta para a conta de alguém.
 */
class CalculadoraIndicadores {

    private static final String[] MESES = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out",
            "nov", "dez"};
    private static final Map<DayOfWeek, String> DIAS = Map.of(DayOfWeek.MONDAY, "seg", DayOfWeek.TUESDAY, "ter",
            DayOfWeek.WEDNESDAY, "qua", DayOfWeek.THURSDAY, "qui", DayOfWeek.FRIDAY, "sex", DayOfWeek.SATURDAY, "sáb",
            DayOfWeek.SUNDAY, "dom");
    private static final Map<CategoriaProduto, String> CATEGORIAS = Map.of(CategoriaProduto.CONSUMO, "Consumo",
            CategoriaProduto.PASSEIO, "Passeio", CategoriaProduto.TAXA, "Taxa", CategoriaProduto.SERVICO, "Serviço");

    /** Consumo com data: um lançamento não estornado na conta, ou um item de venda avulsa concluída. */
    record Consumo(LocalDate data, UUID reservaId, String nome, CategoriaProduto categoria, int quantidade,
                   double total) {
    }

    /**
     * @param reservas    todas as que tocam o período pedido ou o anterior (qualquer status)
     * @param primeiraEstadia por titular, o menor checkin entre as reservas ativas (recorrência)
     */
    record Dados(List<Imovel> casas, List<Canal> canais, List<Reserva> reservas, Map<UUID, LocalDate> primeiraEstadia,
                 List<Consumo> consumos, List<Pagamento> pagamentos, List<Despesa> despesas,
                 Function<Reserva, LocalDate> criadaEm) {
    }

    private record Periodo(LocalDate inicio, LocalDate fim) {

        boolean contem(LocalDate data) {
            return data != null && !data.isBefore(inicio) && !data.isAfter(fim);
        }

        int dias() {
            return (int) ChronoUnit.DAYS.between(inicio, fim) + 1;
        }
    }

    private final Dados dados;
    private final int n;
    private final List<Reserva> ativas;

    CalculadoraIndicadores(Dados dados) {
        this.dados = dados;
        this.n = dados.casas().size();
        this.ativas = dados.reservas().stream().filter(r -> r.getStatus().ativa()).toList();
    }

    IndicadoresResponse indicadores(LocalDate inicio, LocalDate fim) {
        Periodo p = new Periodo(inicio, fim);
        Periodo anterior = new Periodo(inicio.minusDays(p.dias()), inicio.minusDays(1));
        List<Reserva> chegadas = ativas.stream().filter(r -> p.contem(r.getCheckin())).toList();
        List<Reserva> comFnrh = chegadas.stream().filter(r -> r.getFnrh() != null).toList();
        return new IndicadoresResponse(inicio, fim, resumo(p), resumo(anterior), mensal(p),
                ocupacaoPorDia(p).stream().map(d -> new OcupacaoDiaria(d.data, razao(d.ocupadas, n))).toList(),
                porCanal(p), porUh(p), porDiaSemana(p),
                faixas(chegadas, List.of("No dia", "1 a 7 dias", "8 a 30 dias", "31 a 60 dias", "Mais de 60"), r -> {
                    long a = ChronoUnit.DAYS.between(dados.criadaEm().apply(r), r.getCheckin());
                    return a <= 0 ? 0 : a <= 7 ? 1 : a <= 30 ? 2 : a <= 60 ? 3 : 4;
                }),
                faixas(chegadas, List.of("1 noite", "2 noites", "3 noites", "4 a 6 noites", "7 ou mais"), r -> {
                    int noites = r.getNoites();
                    return noites <= 3 ? noites - 1 : noites <= 6 ? 3 : 4;
                }),
                contar(chegadas, r -> r.getCriancas() > 0 ? "Família com crianças" : r.getAdultos() == 1 ? "Sozinho(a)"
                        : r.getAdultos() == 2 ? "Casal ou dupla" : "Grupo de adultos", r -> 1),
                contar(chegadas.stream().filter(r -> origem(r).brasil()).toList(), r -> origem(r).local(), r -> 1),
                contar(chegadas, r -> origem(r).brasil() ? "Brasil" : origem(r).local(), r -> 1),
                contar(comFnrh, r -> r.getFnrh().getMotivoViagem().rotulo(), r -> 1),
                contar(comFnrh, r -> r.getFnrh().getMeioTransporte().rotulo(), r -> 1),
                contar(chegadas, r -> r.getRegimePensao().rotulo(), r -> 1),
                porFormaPagamento(p),
                contar(dados.despesas().stream().filter(d -> p.contem(d.getVencimento())).toList(),
                        Despesa::getCategoria, d -> d.getValor().doubleValue()),
                produtos(p), clientes(chegadas));
    }

    PrevisaoResponse previsao(LocalDate inicio, int quantidade, int vendidas7, int vendidas30) {
        Periodo p = new Periodo(inicio, inicio.plusDays(quantidade - 1));
        Map<LocalDate, Long> chegadas = ativas.stream().filter(r -> p.contem(r.getCheckin()))
                .collect(Collectors.groupingBy(Reserva::getCheckin, Collectors.counting()));
        List<Dia> porDia = ocupacaoPorDia(p);
        List<PrevisaoResponse.Dia> dias = porDia.stream().map(d -> new PrevisaoResponse.Dia(d.data,
                razao(d.ocupadas, n), dinheiro(d.receita), chegadas.getOrDefault(d.data, 0L).intValue())).toList();
        List<PrevisaoResponse.Semana> semanas = new ArrayList<>();
        for (int i = 0; i < porDia.size(); i += 7) {
            List<Dia> fatia = porDia.subList(i, Math.min(i + 7, porDia.size()));
            semanas.add(new PrevisaoResponse.Semana(fatia.getFirst().data,
                    fatia.stream().mapToDouble(d -> razao(d.ocupadas, n)).average().orElse(0),
                    dinheiro(fatia.stream().mapToDouble(d -> d.receita).sum())));
        }
        Function<Integer, Double> media = limite -> porDia.stream().limit(limite)
                .mapToDouble(d -> razao(d.ocupadas, n)).average().orElse(0);
        return new PrevisaoResponse(inicio, dias, semanas, porDia.stream().mapToInt(d -> d.ocupadas).sum(),
                dinheiro(porDia.stream().mapToDouble(d -> d.receita).sum()), media.apply(30), media.apply(60),
                media.apply(90), vendidas7, vendidas30);
    }

    private Resumo resumo(Periodo p) {
        int noitesVendidas = 0;
        double receitaHospedagem = 0;
        double comissoes = 0;
        for (Reserva r : ativas) {
            int noites = noitesEm(r, p).size();
            if (noites == 0) {
                continue;
            }
            double receita = noites * diaria(r);
            noitesVendidas += noites * peso(r);
            receitaHospedagem += receita;
            comissoes += receita * r.getComissaoCanal().doubleValue() / 100;
        }
        double receitaConsumo = dados.consumos().stream().filter(c -> p.contem(c.data())).mapToDouble(Consumo::total).sum();
        double receitaRetida = dados.reservas().stream()
                .filter(r -> r.getEncerramento() != null && p.contem(r.getEncerramento().getData()))
                .mapToDouble(r -> r.getEncerramento().getValorRetido().doubleValue()).sum();
        double taxas = dados.pagamentos().stream().filter(x -> p.contem(x.getData()))
                .mapToDouble(x -> x.getTaxa().doubleValue()).sum();
        double despesas = dados.despesas().stream().filter(d -> p.contem(d.getVencimento()))
                .mapToDouble(d -> d.getValor().doubleValue()).sum();

        List<Reserva> todasChegadas = dados.reservas().stream().filter(r -> p.contem(r.getCheckin())).toList();
        List<Reserva> chegadas = todasChegadas.stream().filter(r -> r.getStatus().ativa()).toList();
        int cancelamentos = (int) todasChegadas.stream().filter(r -> r.getStatus() == StatusReserva.CANCELADA).count();
        int noShows = (int) todasChegadas.stream().filter(r -> r.getStatus() == StatusReserva.NO_SHOW).count();
        long recorrentes = chegadas.stream().filter(r -> {
            LocalDate primeira = dados.primeiraEstadia().get(r.getTitular().getId());
            return primeira != null && primeira.isBefore(r.getCheckin());
        }).count();
        int disponiveis = n * p.dias();
        return new Resumo(razao(noitesVendidas, disponiveis), dinheiro(divisao(receitaHospedagem, noitesVendidas)),
                dinheiro(divisao(receitaHospedagem, disponiveis)), noitesVendidas, dinheiro(receitaHospedagem),
                dinheiro(receitaConsumo), dinheiro(receitaRetida), dinheiro(comissoes), dinheiro(taxas),
                dinheiro(despesas),
                dinheiro(receitaHospedagem + receitaConsumo + receitaRetida - comissoes - taxas - despesas),
                chegadas.size(), chegadas.stream().mapToInt(r -> r.getAdultos() + r.getCriancas()).sum(),
                media(chegadas, Reserva::getNoites),
                media(chegadas, r -> ChronoUnit.DAYS.between(dados.criadaEm().apply(r), r.getCheckin())),
                cancelamentos, noShows, razao(cancelamentos + noShows, todasChegadas.size()),
                razao(recorrentes, chegadas.size()));
    }

    private List<Mensal> mensal(Periodo p) {
        List<Mensal> meses = new ArrayList<>();
        for (YearMonth mes = YearMonth.from(p.inicio()); !mes.atDay(1).isAfter(p.fim()); mes = mes.plusMonths(1)) {
            Periodo fatia = new Periodo(max(mes.atDay(1), p.inicio()), min(mes.atEndOfMonth(), p.fim()));
            Resumo r = resumo(fatia);
            meses.add(new Mensal(MESES[mes.getMonthValue() - 1] + "/" + String.valueOf(mes.getYear()).substring(2),
                    r.ocupacao(), r.adr(), r.revpar(), r.receitaHospedagem(), r.receitaConsumo().add(r.receitaRetida()),
                    r.despesas(), r.comissoes().add(r.taxasCartao()), r.resultado()));
        }
        return meses;
    }

    private List<IndicadoresResponse.Canal> porCanal(Periodo p) {
        return dados.canais().stream().map(canal -> {
            List<Reserva> doCanal = dados.reservas().stream()
                    .filter(r -> r.getCanal().getId().equals(canal.getId())).toList();
            int noites = 0;
            double receita = 0;
            double comissao = 0;
            for (Reserva r : doCanal.stream().filter(r -> r.getStatus().ativa()).toList()) {
                int noitesNoPeriodo = noitesEm(r, p).size();
                noites += noitesNoPeriodo * peso(r);
                receita += noitesNoPeriodo * diaria(r);
                comissao += noitesNoPeriodo * diaria(r) * r.getComissaoCanal().doubleValue() / 100;
            }
            List<Reserva> chegadas = doCanal.stream().filter(r -> p.contem(r.getCheckin())).toList();
            return new IndicadoresResponse.Canal(canal.getId(), canal.getNome(), canal.getCor(),
                    (int) chegadas.stream().filter(r -> r.getStatus().ativa()).count(), noites, dinheiro(receita),
                    dinheiro(comissao), dinheiro(receita - comissao),
                    (int) chegadas.stream().filter(r -> !r.getStatus().ativa()).count());
        }).sorted(Comparator.comparing(IndicadoresResponse.Canal::receita).reversed()).toList();
    }

    private List<IndicadoresResponse.Uh> porUh(Periodo p) {
        int dias = p.dias();
        return dados.casas().stream().map(casa -> {
            int noites = 0;
            double receita = 0;
            for (Reserva r : ativas) {
                boolean completo = completo(r);
                if (!completo && !r.getImovel().getId().equals(casa.getId())) {
                    continue;
                }
                int noitesNoPeriodo = noitesEm(r, p).size();
                noites += noitesNoPeriodo;
                // A diária do imóvel completo é dividida igualmente entre as casas.
                receita += noitesNoPeriodo * diaria(r) / (completo ? n : 1);
            }
            Set<UUID> daCasa = ativas.stream().filter(r -> r.getImovel().getId().equals(casa.getId()))
                    .map(Reserva::getId).collect(Collectors.toSet());
            double consumo = dados.consumos().stream()
                    .filter(c -> c.reservaId() != null && daCasa.contains(c.reservaId()) && p.contem(c.data()))
                    .mapToDouble(Consumo::total).sum();
            return new IndicadoresResponse.Uh(casa.getId(), casa.getNome(), razao(noites, dias), noites,
                    dinheiro(receita), dinheiro(divisao(receita, noites)), dinheiro(consumo));
        }).sorted(Comparator.comparing(IndicadoresResponse.Uh::receita).reversed()).toList();
    }

    private List<DiaSemana> porDiaSemana(Periodo p) {
        Map<DayOfWeek, double[]> acumulado = new HashMap<>();
        for (Dia dia : ocupacaoPorDia(p)) {
            double[] a = acumulado.computeIfAbsent(dia.data.getDayOfWeek(), d -> new double[3]);
            a[0] += 1;
            a[1] += dia.ocupadas;
            a[2] += dia.receita;
        }
        // Semana começando na segunda, como no calendário da recepção.
        List<DiaSemana> semana = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            double[] a = acumulado.getOrDefault(dia, new double[3]);
            semana.add(new DiaSemana(DIAS.get(dia), razao(a[1], a[0] * n), dinheiro(divisao(a[2], a[1]))));
        }
        return semana;
    }

    private record Origem(String local, boolean brasil) {
    }

    /** Procedência da FNRH ou, sem ela, o endereço do titular: "Cidade/UF" ou "Cidade/País". */
    private Origem origem(Reserva r) {
        Hospede titular = r.getTitular();
        String texto = r.getFnrh() != null ? r.getFnrh().getCidadeProcedencia()
                : Objects.requireNonNullElse(titular.getEndereco(), "Não informado");
        String[] partes = texto.split("/");
        String local = partes[partes.length - 1].trim();
        if (local.isEmpty()) {
            local = "Não informado";
        }
        boolean uf = local.matches("[A-Z]{2}");
        boolean brasileiro = titular.getNacionalidade() != null
                && titular.getNacionalidade().trim().toLowerCase().startsWith("brasil");
        return new Origem(uf || !brasileiro ? local : "Não informado", uf || brasileiro);
    }

    private List<IndicadoresResponse.FormaPagamento> porFormaPagamento(Periodo p) {
        Map<com.gestaocasas.domain.enums.FormaPagamento, double[]> porForma = new LinkedHashMap<>();
        for (Pagamento x : dados.pagamentos()) {
            if (p.contem(x.getData())) {
                double[] a = porForma.computeIfAbsent(x.getForma(), f -> new double[2]);
                a[0] += x.getValorBruto().doubleValue();
                a[1] += x.getTaxa().doubleValue();
            }
        }
        return porForma.entrySet().stream()
                .map(e -> new IndicadoresResponse.FormaPagamento(e.getKey().rotulo(), dinheiro(e.getValue()[0]),
                        dinheiro(e.getValue()[1])))
                .sorted(Comparator.comparing(IndicadoresResponse.FormaPagamento::valor).reversed())
                .toList();
    }

    private List<IndicadoresResponse.Produto> produtos(Periodo p) {
        Map<String, Object[]> mapa = new LinkedHashMap<>();
        for (Consumo c : dados.consumos()) {
            if (!p.contem(c.data())) {
                continue;
            }
            Object[] atual = mapa.computeIfAbsent(c.nome(), nome -> new Object[]{
                    c.categoria() == null ? "Outro" : CATEGORIAS.get(c.categoria()), 0, 0.0});
            atual[1] = (int) atual[1] + c.quantidade();
            atual[2] = (double) atual[2] + c.total();
        }
        return mapa.entrySet().stream()
                .map(e -> new IndicadoresResponse.Produto(e.getKey(), (String) e.getValue()[0], (int) e.getValue()[1],
                        dinheiro((double) e.getValue()[2])))
                .sorted(Comparator.comparing(IndicadoresResponse.Produto::receita).reversed())
                .limit(8)
                .toList();
    }

    private List<Cliente> clientes(List<Reserva> chegadas) {
        Map<UUID, List<Reserva>> porTitular = chegadas.stream()
                .collect(Collectors.groupingBy(r -> r.getTitular().getId(), LinkedHashMap::new, Collectors.toList()));
        return porTitular.values().stream().map(estadias -> {
            Hospede titular = estadias.getFirst().getTitular();
            return new Cliente(titular.getId(), titular.getNome(),
                    Objects.requireNonNullElse(titular.getEndereco(), titular.getNacionalidade()), estadias.size(),
                    estadias.stream().mapToInt(Reserva::getNoites).sum(),
                    dinheiro(estadias.stream().mapToDouble(r -> r.getValorTotal().doubleValue()).sum()));
        }).sorted(Comparator.comparingInt(Cliente::estadias).reversed()
                .thenComparing(Comparator.comparing(Cliente::receita).reversed()))
                .limit(8)
                .toList();
    }

    private static final class Dia {
        final LocalDate data;
        int ocupadas;
        double receita;

        Dia(LocalDate data) {
            this.data = data;
        }
    }

    private List<Dia> ocupacaoPorDia(Periodo p) {
        Map<LocalDate, Dia> mapa = new LinkedHashMap<>();
        for (LocalDate d = p.inicio(); !d.isAfter(p.fim()); d = d.plusDays(1)) {
            mapa.put(d, new Dia(d));
        }
        for (Reserva r : ativas) {
            for (LocalDate noite : noitesEm(r, p)) {
                Dia dia = mapa.get(noite);
                dia.ocupadas += peso(r);
                dia.receita += diaria(r);
            }
        }
        return new ArrayList<>(mapa.values());
    }

    /** Noites da reserva dentro do período. */
    private static List<LocalDate> noitesEm(Reserva r, Periodo p) {
        LocalDate inicio = max(r.getCheckin(), p.inicio());
        LocalDate fim = min(r.getCheckout(), p.fim().plusDays(1));
        List<LocalDate> noites = new ArrayList<>();
        for (LocalDate d = inicio; d.isBefore(fim); d = d.plusDays(1)) {
            noites.add(d);
        }
        return noites;
    }

    private static boolean completo(Reserva r) {
        return r.getImovel().getTipo() == TipoUh.IMOVEL_COMPLETO;
    }

    /** O imóvel completo ocupa todas as casas na noite. */
    private int peso(Reserva r) {
        return completo(r) ? n : 1;
    }

    private static double diaria(Reserva r) {
        return r.getValorTotal().doubleValue() / r.getNoites();
    }

    private static <T> List<Contagem> contar(List<T> itens, Function<T, String> chave, ToDoubleFunction<T> valor) {
        Map<String, Double> mapa = new LinkedHashMap<>();
        for (T item : itens) {
            mapa.merge(chave.apply(item), valor.applyAsDouble(item), Double::sum);
        }
        return mapa.entrySet().stream()
                .map(e -> new Contagem(e.getKey(), dinheiro(e.getValue())))
                .sorted(Comparator.comparing(Contagem::valor).reversed())
                .toList();
    }

    /** Faixas fixas na ordem dada, mesmo as vazias, para o histograma não pular barras. */
    private static <T> List<Contagem> faixas(List<T> itens, List<String> rotulos, ToIntFunction<T> faixa) {
        int[] valores = new int[rotulos.size()];
        itens.forEach(item -> valores[faixa.applyAsInt(item)]++);
        List<Contagem> resultado = new ArrayList<>();
        for (int i = 0; i < rotulos.size(); i++) {
            resultado.add(new Contagem(rotulos.get(i), BigDecimal.valueOf(valores[i])));
        }
        return resultado;
    }

    private static <T> double media(List<T> itens, ToDoubleFunction<T> valor) {
        return arredondar(itens.stream().mapToDouble(valor).average().orElse(0), 2);
    }

    private static double razao(double parte, double total) {
        return total == 0 ? 0 : arredondar(parte / total, 4);
    }

    private static double divisao(double valor, double total) {
        return total == 0 ? 0 : valor / total;
    }

    private static double arredondar(double valor, int casas) {
        return BigDecimal.valueOf(valor).setScale(casas, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal dinheiro(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
