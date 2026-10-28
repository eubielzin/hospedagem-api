package com.gestaocasas.infrastructure.demo;

import com.gestaocasas.application.dto.CotacaoResponse;
import com.gestaocasas.application.service.CotadorReserva;
import com.gestaocasas.application.validation.Cpf;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Despesa;
import com.gestaocasas.domain.entity.Encerramento;
import com.gestaocasas.domain.entity.Fnrh;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.LancamentoConta;
import com.gestaocasas.domain.entity.Pagamento;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.FormaPagamento;
import com.gestaocasas.domain.enums.MeioTransporte;
import com.gestaocasas.domain.enums.MotivoViagem;
import com.gestaocasas.domain.enums.RegimePensao;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoCanal;
import com.gestaocasas.domain.enums.TipoDocumento;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.CanalRepository;
import com.gestaocasas.domain.repository.DespesaRepository;
import com.gestaocasas.domain.repository.HospedeRepository;
import com.gestaocasas.domain.repository.LancamentoContaRepository;
import com.gestaocasas.domain.repository.PagamentoRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Movimento sintético de demonstração (RN-16): hóspedes, reservas de 60 dias atrás a 45 dias à frente, FNRH,
 * consumos, pagamentos, repasses e despesas mensais. Sempre o mesmo sorteio (semente fixa). As comissões das OTAs
 * são as do protótipo e ficam só na demonstração (a validar).
 */
@Component
class MovimentoDemonstracao {

    private static final String[][] HOSPEDES = {
            {"Ana Beatriz Lima", "Brasileira", "São Luís/MA", "Sem lactose"},
            {"Bruno Carvalho", "Brasileiro", "Teresina/PI", null},
            {"Camila Rocha", "Brasileira", "Fortaleza/CE", "Vegetariana"},
            {"Diego Martins", "Brasileiro", "Belém/PA", null},
            {"Elisa Fontes", "Brasileira", "Brasília/DF", null},
            {"Felipe Andrade", "Brasileiro", "São Paulo/SP", "Alergia a camarão"},
            {"Gabriela Nunes", "Brasileira", "Recife/PE", null},
            {"Henrique Prado", "Brasileiro", "Belo Horizonte/MG", null},
            {"Isabela Costa", "Brasileira", "Rio de Janeiro/RJ", "Sem glúten"},
            {"João Pedro Alves", "Brasileiro", "Imperatriz/MA", null},
            {"Laura Schmidt", "Alemã", "Berlim/Alemanha", null},
            {"Martín Gómez", "Argentino", "Córdoba/Argentina", "Vegano"},
    };
    private static final String[] CONSUMOS = {"Água 500ml", "Refrigerante lata", "Cerveja long neck",
            "Passeio aos Lençóis", "Lavanderia", "Café especial"};
    private static final int[] PRECOS = {5, 7, 12, 180, 40, 15};
    private static final FormaPagamento[] FORMAS = {FormaPagamento.PIX, FormaPagamento.CREDITO,
            FormaPagamento.DEBITO, FormaPagamento.DINHEIRO};

    private final HospedeRepository hospedeRepository;
    private final CanalRepository canalRepository;
    private final ReservaRepository reservaRepository;
    private final LancamentoContaRepository lancamentoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final DespesaRepository despesaRepository;
    private final CotadorReserva cotador;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    MovimentoDemonstracao(HospedeRepository hospedeRepository, CanalRepository canalRepository,
                          ReservaRepository reservaRepository, LancamentoContaRepository lancamentoRepository,
                          PagamentoRepository pagamentoRepository, DespesaRepository despesaRepository,
                          CotadorReserva cotador, JdbcTemplate jdbc, Clock clock) {
        this.hospedeRepository = hospedeRepository;
        this.canalRepository = canalRepository;
        this.reservaRepository = reservaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.despesaRepository = despesaRepository;
        this.cotador = cotador;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return quantas reservas foram criadas */
    int criar(List<Imovel> casas) {
        Random sorteio = new Random(42);
        List<Canal> canais = canais();
        List<Hospede> hospedes = hospedes();
        LocalDate hoje = LocalDate.now(clock);
        int reservas = 0;
        for (Imovel casa : casas) {
            LocalDate dia = hoje.minusDays(60 + sorteio.nextInt(3));
            while (dia.isBefore(hoje.plusDays(45))) {
                int noites = 1 + sorteio.nextInt(5);
                Canal canal = canais.get(sorteio.nextInt(canais.size()));
                Hospede titular = hospedes.get(sorteio.nextInt(hospedes.size()));
                if (reservar(casa, canal, titular, dia, noites, sorteio, hoje)) {
                    reservas++;
                    dia = dia.plusDays(noites);
                }
                dia = dia.plusDays(sorteio.nextInt(4));
            }
        }
        despesas(sorteio, hoje);
        return reservas;
    }

    private boolean reservar(Imovel casa, Canal canal, Hospede titular, LocalDate checkin, int noites,
                             Random sorteio, LocalDate hoje) {
        LocalDate checkout = checkin.plusDays(noites);
        CotacaoResponse cotacao;
        try {
            cotacao = cotador.cotar(casa, checkin, checkout);
        } catch (RegraDeNegocioException restricao) {
            return false; // estadia mínima da alta temporada: tenta outro período
        }
        Reserva reserva = new Reserva();
        reserva.setCodigo("CM-" + reservaRepository.proximoNumero());
        reserva.setImovel(casa);
        reserva.setTitular(titular);
        reserva.setCanal(canal);
        reserva.setComissaoCanal(canal.getComissao());
        reserva.setCheckin(checkin);
        reserva.setCheckout(checkout);
        reserva.setAdultos(1 + sorteio.nextInt(Math.min(3, casa.getCapacidadeAdultos())));
        reserva.setCriancas(sorteio.nextInt(4) == 0 ? 1 : 0);
        reserva.setValorTotal(cotacao.valorTotal());
        reserva.setRegimePensao(sorteio.nextBoolean() ? RegimePensao.CAFE_DA_MANHA : RegimePensao.SEM_REFEICOES);
        if (canal.getTipo() == TipoCanal.OTA) {
            reserva.setCodigoExterno(canal.getNome().substring(0, 3).toUpperCase() + (100000 + sorteio.nextInt(900000)));
        }
        Instant agora = clock.instant();
        reserva.registrar("Reserva criada (" + canal.getNome() + ") – demonstração", agora, null);

        int sorte = sorteio.nextInt(12);
        if (!checkout.isAfter(hoje)) {
            if (sorte == 0) {
                encerrar(reserva, StatusReserva.NO_SHOW, "Não compareceu", checkin, sorteio);
            } else if (sorte == 1) {
                encerrar(reserva, StatusReserva.CANCELADA, "Mudança de planos", checkin.minusDays(5), sorteio);
            } else {
                fnrh(reserva, sorteio);
                reserva.setStatus(StatusReserva.CHECKOUT_REALIZADO);
                reserva.setCheckinRealizadoEm(checkin.atTime(14, 30).atZone(clock.getZone()).toInstant());
                reserva.setCheckoutRealizadoEm(checkout.atTime(11, 0).atZone(clock.getZone()).toInstant());
            }
        } else if (!checkin.isAfter(hoje)) {
            fnrh(reserva, sorteio);
            reserva.setStatus(StatusReserva.HOSPEDADO);
            reserva.setCheckinRealizadoEm(checkin.atTime(14, 30).atZone(clock.getZone()).toInstant());
        } else if (sorte == 0) {
            encerrar(reserva, StatusReserva.CANCELADA, "Desistência", hoje.minusDays(sorteio.nextInt(5)), sorteio);
        } else {
            reserva.setStatus(StatusReserva.RESERVADO);
        }
        reservaRepository.save(reserva);
        // A antecedência dos indicadores vem da data em que a reserva foi feita.
        LocalDate feitaEm = checkin.minusDays(sorteio.nextInt(60));
        if (feitaEm.isAfter(hoje)) {
            feitaEm = hoje;
        }
        reservaRepository.flush();
        jdbc.update("UPDATE reserva SET criado_em = ? WHERE id = ?",
                Timestamp.from(feitaEm.atTime(10, 0).atZone(clock.getZone()).toInstant()), reserva.getId());

        if (reserva.getStatus() == StatusReserva.HOSPEDADO || reserva.getStatus() == StatusReserva.CHECKOUT_REALIZADO) {
            conta(reserva, sorteio, hoje);
        }
        return true;
    }

    private void encerrar(Reserva reserva, StatusReserva status, String motivo, LocalDate data, Random sorteio) {
        BigDecimal multa = sorteio.nextBoolean() ? BigDecimal.ZERO.setScale(2)
                : reserva.getValorTotal().multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        Encerramento encerramento = new Encerramento();
        encerramento.setData(data);
        encerramento.setMotivo(motivo);
        encerramento.setMulta(multa);
        encerramento.setValorRetido(multa);
        encerramento.setValorDevolver(BigDecimal.ZERO.setScale(2));
        reserva.setStatus(status);
        reserva.setEncerramento(encerramento);
        reserva.setEncerradaEm(data.atTime(9, 0).atZone(clock.getZone()).toInstant());
    }

    private void fnrh(Reserva reserva, Random sorteio) {
        Fnrh fnrh = new Fnrh();
        fnrh.setCidadeProcedencia(reserva.getTitular().getEndereco());
        fnrh.setCidadeDestino("Barreirinhas/MA");
        fnrh.setMotivoViagem(sorteio.nextInt(5) == 0 ? MotivoViagem.PARENTES : MotivoViagem.LAZER);
        fnrh.setMeioTransporte(MeioTransporte.values()[sorteio.nextInt(3)]);
        fnrh.setEmitidaEm(reserva.getCheckin());
        reserva.setFnrh(fnrh);
    }

    /** Consumos e pagamentos: quem já saiu quitou a conta; quem está na casa pagou parte. */
    private void conta(Reserva reserva, Random sorteio, LocalDate hoje) {
        BigDecimal consumos = BigDecimal.ZERO;
        for (int i = sorteio.nextInt(3); i > 0; i--) {
            int item = sorteio.nextInt(CONSUMOS.length);
            int quantidade = 1 + sorteio.nextInt(3);
            LancamentoConta lancamento = new LancamentoConta();
            lancamento.setReserva(reserva);
            lancamento.setData(reserva.getCheckin().plusDays(sorteio.nextInt(reserva.getNoites())));
            lancamento.setDescricao(CONSUMOS[item]);
            lancamento.setQuantidade(quantidade);
            lancamento.setValorUnitario(BigDecimal.valueOf(PRECOS[item]).setScale(2));
            lancamento.setTotal(lancamento.getValorUnitario().multiply(BigDecimal.valueOf(quantidade)));
            lancamentoRepository.save(lancamento);
            consumos = consumos.add(lancamento.getTotal());
        }
        boolean ota = reserva.getCanal().getTipo() == TipoCanal.OTA;
        BigDecimal devido = (ota ? BigDecimal.ZERO : reserva.getValorTotal()).add(consumos);
        boolean saiu = reserva.getStatus() == StatusReserva.CHECKOUT_REALIZADO;
        BigDecimal pago = saiu ? devido : devido.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (pago.signum() > 0) {
            FormaPagamento forma = FORMAS[sorteio.nextInt(FORMAS.length)];
            BigDecimal taxa = forma == FormaPagamento.CREDITO ? pago.multiply(new BigDecimal("0.03"))
                    .setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
            Pagamento pagamento = new Pagamento();
            pagamento.setReserva(reserva);
            pagamento.setData(saiu ? reserva.getCheckout() : reserva.getCheckin());
            pagamento.setForma(forma);
            pagamento.setValorBruto(pago);
            pagamento.setTaxa(taxa);
            pagamento.setValorLiquido(pago.subtract(taxa));
            pagamento.setDescricao("Recebimento da reserva " + reserva.getCodigo());
            pagamentoRepository.save(pagamento);
        }
        // Repasse das OTAs: recebido uma semana depois da saída, quando já passou.
        if (ota && saiu && !reserva.getCheckout().plusDays(7).isAfter(hoje)) {
            reserva.setRepasseRecebidoEm(reserva.getCheckout().plusDays(7));
            reservaRepository.save(reserva);
        }
    }

    private void despesas(Random sorteio, LocalDate hoje) {
        String[][] fixas = {{"Energia", "Equatorial Maranhão", "900"}, {"Água", "CAEMA", "250"},
                {"Internet", "Provedor Dunas", "180"}, {"Lavanderia", "Lava Bem", "600"},
                {"Limpeza", "Mercado Central", "350"}};
        for (YearMonth mes = YearMonth.from(hoje.minusMonths(2)); !mes.isAfter(YearMonth.from(hoje.plusMonths(1)));
             mes = mes.plusMonths(1)) {
            for (String[] fixa : fixas) {
                Despesa despesa = new Despesa();
                despesa.setCategoria(fixa[0]);
                despesa.setFornecedor(fixa[1]);
                despesa.setValor(new BigDecimal(fixa[2]).add(BigDecimal.valueOf(sorteio.nextInt(100))).setScale(2));
                despesa.setVencimento(mes.atDay(10));
                if (despesa.getVencimento().isBefore(hoje)) {
                    despesa.setDataPagamento(despesa.getVencimento().minusDays(sorteio.nextInt(3)));
                }
                despesaRepository.save(despesa);
            }
        }
    }

    /** Comissões e cores do protótipo; acrescenta os canais que a migração não criou. */
    private List<Canal> canais() {
        String[][] dados = {{"Direto (recepção)", "DIRETO", "0", "#0f766e"}, {"Motor de reservas", "MOTOR", "0", "#7c3aed"},
                {"Booking.com", "OTA", "15", "#1d4ed8"}, {"Airbnb", "OTA", "15", "#e11d48"},
                {"Expedia", "OTA", "18", "#ca8a04"}, {"Hotels.com", "OTA", "18", "#c2410c"}};
        List<Canal> canais = new ArrayList<>();
        for (String[] d : dados) {
            Canal canal = canalRepository.findAll().stream().filter(c -> c.getNome().equalsIgnoreCase(d[0])).findFirst()
                    .orElseGet(Canal::new);
            canal.setNome(d[0]);
            canal.setTipo(TipoCanal.valueOf(d[1]));
            canal.setComissao(new BigDecimal(d[2]));
            canal.setCor(d[3]);
            canal.setAtivo(true);
            canais.add(canalRepository.save(canal));
        }
        return canais;
    }

    private List<Hospede> hospedes() {
        List<Hospede> hospedes = new ArrayList<>();
        for (int i = 0; i < HOSPEDES.length; i++) {
            String[] h = HOSPEDES[i];
            boolean brasileiro = h[1].startsWith("Brasileir");
            Hospede hospede = new Hospede();
            hospede.setNome(h[0]);
            hospede.setTipoDocumento(brasileiro ? TipoDocumento.CPF : TipoDocumento.PASSAPORTE);
            hospede.setNumeroDocumento(brasileiro ? cpf(String.format("%09d", 123456780 + i * 7919)) : "PX" + (482910 + i));
            hospede.setNacionalidade(h[1]);
            hospede.setEndereco(h[2]);
            hospede.setObservacoesAlimentares(h[3]);
            hospede.setTelefone("(98) 9" + (8100 + i) + "-" + (2000 + i * 37));
            hospede.setEmail(h[0].toLowerCase().split(" ")[0] + "@exemplo.com");
            hospede.setDataNascimento(LocalDate.of(1970 + i * 3, 1 + i % 12, 1 + i * 2));
            hospede.setAtivo(true);
            hospedes.add(hospedeRepository.save(hospede));
        }
        return hospedes;
    }

    /** Completa 9 dígitos com os dois verificadores: CPFs sintéticos, mas válidos. */
    private static String cpf(String noveDigitos) {
        for (int d1 = 0; d1 <= 9; d1++) {
            for (int d2 = 0; d2 <= 9; d2++) {
                String candidato = noveDigitos + d1 + d2;
                if (Cpf.valido(candidato)) {
                    return candidato;
                }
            }
        }
        throw new IllegalStateException("Sem CPF válido para " + noveDigitos);
    }
}
