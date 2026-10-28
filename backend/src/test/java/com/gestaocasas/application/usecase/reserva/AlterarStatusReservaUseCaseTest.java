package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.EncerramentoRequest;
import com.gestaocasas.application.mapper.ReservaMapperImpl;
import com.gestaocasas.application.service.GovernancaUh;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.entity.Fnrh;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.enums.StatusGovernanca;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.enums.TipoUh;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.BloqueioRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlterarStatusReservaUseCaseTest {

    // 02:30 UTC de 01/03 = 23:30 de 28/02 em Brasília. O "hoje" das regras é 28/02.
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final Instant AGORA = Instant.parse("2027-03-01T02:30:00Z");

    @Mock
    private ReservaRepository repository;
    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private BloqueioRepository bloqueioRepository;
    @Mock
    private UsuarioLogado usuarioLogado;

    private AlterarStatusReservaUseCase useCase;
    private EncerrarReservaUseCase encerrar;
    private Reserva reserva;
    private Imovel casa;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA, BRASILIA);
        AcoesReserva acoes = new AcoesReserva(repository, usuarioLogado, clock);
        GovernancaUh governanca = new GovernancaUh(imovelRepository, repository, bloqueioRepository, clock);
        useCase = new AlterarStatusReservaUseCase(repository, governanca, acoes, new ReservaMapperImpl());
        encerrar = new EncerrarReservaUseCase(repository, acoes, new ReservaMapperImpl());

        Usuario recepcao = new Usuario();
        recepcao.setNome("Recepção");
        lenient().when(usuarioLogado.atual()).thenReturn(Optional.of(recepcao));

        Hospede titular = new Hospede();
        titular.setNome("Ana");
        titular.setNumeroDocumento("52998224725");
        casa = new Imovel();
        casa.setId(UUID.randomUUID());
        casa.setNome("Casa Flor");
        casa.setTipo(TipoUh.CASA);
        casa.setStatus(StatusImovel.ATIVO);
        casa.setStatusGovernanca(StatusGovernanca.LIVRE);
        Canal canal = new Canal();
        canal.setNome("Direto");
        reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setTitular(titular);
        reserva.setImovel(casa);
        reserva.setCanal(canal);
        reserva.setStatus(StatusReserva.RESERVADO);
        reserva.setCheckin(LocalDate.of(2027, 2, 28));
        reserva.setCheckout(LocalDate.of(2027, 3, 2));
        reserva.setValorTotal(new BigDecimal("800.00"));
        reserva.setFnrh(new Fnrh());
        lenient().when(repository.findDetalhadaById(reserva.getId())).thenReturn(Optional.of(reserva));
        lenient().when(repository.saveAndFlush(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hojeEOdiaEmBrasiliaNaoEmUtc() {
        // 02:30 UTC do dia 01/03 ainda é 28/02 em Brasília: entrada em 01/03 não pode fazer check-in.
        reserva.setCheckin(LocalDate.of(2027, 3, 1));

        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O check-in só pode ser feito a partir de 01/03/2027.");
    }

    @Test
    void checkinNoDiaDaEntradaRegistraOMomentoEOHistorico() {
        assertThat(useCase.checkin(reserva.getId()).status()).isEqualTo(StatusReserva.HOSPEDADO);
        assertThat(reserva.getCheckinRealizadoEm()).isEqualTo(AGORA);
        assertThat(reserva.getHistorico()).singleElement().satisfies(evento -> {
            assertThat(evento.getDescricao()).isEqualTo("Check-in realizado");
            assertThat(evento.getDataHora()).isEqualTo(AGORA);
            assertThat(evento.getUsuario().getNome()).isEqualTo("Recepção");
        });
    }

    @Test
    void checkinExigeDocumentoEFnrh() {
        reserva.getTitular().setNumeroDocumento("");
        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Informe o documento do hóspede titular antes do check-in.")
                .extracting("codigo").isEqualTo("RN-04");

        reserva.getTitular().setNumeroDocumento("52998224725");
        reserva.setFnrh(null);
        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .hasMessage("Preencha a FNRH antes do check-in.")
                .extracting("codigo").isEqualTo("RN-04");
        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.RESERVADO);
    }

    @Test
    void checkinExigeUhLiberadaPelaGovernanca() {
        casa.setStatusGovernanca(StatusGovernanca.SUJA);

        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .hasMessage("Casa Flor ainda não foi liberada pela governança.")
                .extracting("codigo").isEqualTo("RF-35");
    }

    @Test
    void checkinNoImovelCompletoExigeTodasAsCasasLiberadas() {
        casa.setTipo(TipoUh.IMOVEL_COMPLETO);
        Imovel suja = new Imovel();
        suja.setNome("Casa Alegria");
        suja.setTipo(TipoUh.CASA);
        suja.setStatus(StatusImovel.ATIVO);
        suja.setStatusGovernanca(StatusGovernanca.MANUTENCAO);
        when(imovelRepository.findByTipo(TipoUh.CASA)).thenReturn(List.of(suja));

        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .hasMessage("Casa Alegria ainda não foi liberada pela governança.");
    }

    @Test
    void checkoutSoDeHospedadoEDeixaAUhSuja() {
        assertThatThrownBy(() -> useCase.checkout(reserva.getId()))
                .hasMessage("Não é possível fazer o check-out com a reserva em \"Reservado\".")
                .extracting("codigo").isEqualTo("RN-03");

        reserva.setStatus(StatusReserva.HOSPEDADO);
        assertThat(useCase.checkout(reserva.getId()).status()).isEqualTo(StatusReserva.CHECKOUT_REALIZADO);
        assertThat(reserva.getCheckoutRealizadoEm()).isEqualTo(AGORA);
        assertThat(casa.getStatusGovernanca()).isEqualTo(StatusGovernanca.SUJA);
    }

    @Test
    void cancelamentoGravaOEncerramentoComAsDatasDeBrasilia() {
        var resposta = encerrar.cancelar(reserva.getId(), new EncerramentoRequest("  Desistiu  ",
                new BigDecimal("100"), new BigDecimal("100"), null));

        assertThat(resposta.status()).isEqualTo(StatusReserva.CANCELADA);
        assertThat(resposta.encerramento().data()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(resposta.encerramento().motivo()).isEqualTo("Desistiu");
        assertThat(resposta.encerramento().multa()).isEqualByComparingTo("100");
        assertThat(resposta.encerramento().valorDevolver()).isEqualByComparingTo("0");
        assertThat(resposta.motivoCancelamento()).isEqualTo("Desistiu");
        assertThat(resposta.canceladaEm()).isEqualTo(AGORA);
        assertThat(reserva.getHistorico().getLast().getDescricao())
                .isEqualTo("Cancelada: Desistiu (multa R$ 100,00, retido R$ 100,00, a devolver R$ 0,00)");
    }

    @Test
    void noShowSoDeReservado() {
        reserva.setStatus(StatusReserva.HOSPEDADO);

        assertThatThrownBy(() -> encerrar.noShow(reserva.getId(), new EncerramentoRequest("Não veio", null, null, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Não é possível registrar o no-show com a reserva em \"Hospedado\".");

        reserva.setStatus(StatusReserva.RESERVADO);
        var resposta = encerrar.noShow(reserva.getId(), new EncerramentoRequest("Não veio", null, null, null));
        assertThat(resposta.status()).isEqualTo(StatusReserva.NO_SHOW);
        // Os campos antigos repetem só o cancelamento.
        assertThat(resposta.motivoCancelamento()).isNull();
        assertThat(resposta.encerramento().motivo()).isEqualTo("Não veio");
    }

    @Test
    void reservaInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findDetalhadaById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkin(id)).isInstanceOf(ReservaNotFoundException.class);
    }
}
