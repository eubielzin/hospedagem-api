package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.dto.CancelamentoReservaRequest;
import com.gestaocasas.application.mapper.ReservaMapperImpl;
import com.gestaocasas.domain.entity.Hospede;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private AlterarStatusReservaUseCase useCase;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        useCase = new AlterarStatusReservaUseCase(repository, new ReservaMapperImpl(), Clock.fixed(AGORA, BRASILIA));
        Hospede titular = new Hospede();
        titular.setNome("Ana");
        titular.setNumeroDocumento("52998224725");
        reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setTitular(titular);
        reserva.setStatus(StatusReserva.RESERVADO);
        reserva.setCheckin(LocalDate.of(2027, 2, 28));
        reserva.setCheckout(LocalDate.of(2027, 3, 2));
        lenient().when(repository.findDetalhadaById(reserva.getId())).thenReturn(Optional.of(reserva));
        lenient().when(repository.saveAndFlush(any(Reserva.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hojeEOdiaEmBrasiliaNaoEmUtc() {
        // 02:30 UTC do dia 01/03 ainda é 28/02 em Brasília: entrada em 01/03 não pode fazer check-in.
        reserva.setCheckin(LocalDate.of(2027, 3, 1));

        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("O check-in só pode ser feito a partir de 01/03/2027");
    }

    @Test
    void checkinNoDiaDaEntradaRegistraOMomento() {
        assertThat(useCase.checkin(reserva.getId()).status()).isEqualTo(StatusReserva.HOSPEDADO);
        assertThat(reserva.getCheckinRealizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void checkinExigeDocumentoDoTitular() {
        reserva.getTitular().setNumeroDocumento("");

        assertThatThrownBy(() -> useCase.checkin(reserva.getId()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("documento");
        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.RESERVADO);
    }

    @Test
    void checkoutSoDeHospedado() {
        assertThatThrownBy(() -> useCase.checkout(reserva.getId())).isInstanceOf(RegraDeNegocioException.class);

        reserva.setStatus(StatusReserva.HOSPEDADO);
        assertThat(useCase.checkout(reserva.getId()).status()).isEqualTo(StatusReserva.CHECKOUT_REALIZADO);
        assertThat(reserva.getCheckoutRealizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void cancelamentoGuardaMotivoSemEspacosEAceitaCorpoVazio() {
        assertThat(useCase.cancelar(reserva.getId(), new CancelamentoReservaRequest("  Desistiu  "))
                .motivoCancelamento()).isEqualTo("Desistiu");
        assertThat(reserva.getCanceladaEm()).isEqualTo(AGORA);

        reserva.setStatus(StatusReserva.RESERVADO);
        assertThat(useCase.cancelar(reserva.getId(), null).motivoCancelamento()).isNull();
    }

    @Test
    void hospedadoNaoPodeSerCancelado() {
        reserva.setStatus(StatusReserva.HOSPEDADO);

        assertThatThrownBy(() -> useCase.cancelar(reserva.getId(), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Não é possível cancelar de uma reserva com status HOSPEDADO");
    }

    @Test
    void reservaInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findDetalhadaById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.checkin(id))
                .isInstanceOf(com.gestaocasas.domain.exception.ReservaNotFoundException.class);
    }
}
