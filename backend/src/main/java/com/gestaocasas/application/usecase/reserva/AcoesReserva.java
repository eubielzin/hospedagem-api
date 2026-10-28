package com.gestaocasas.application.usecase.reserva;

import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Reserva;
import com.gestaocasas.domain.enums.StatusReserva;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.ReservaNotFoundException;
import com.gestaocasas.domain.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

/** O que as ações sobre uma reserva têm em comum: busca, ciclo de vida (RN-03) e histórico (RN-14). */
@Component
public class AcoesReserva {

    private final ReservaRepository repository;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    public AcoesReserva(ReservaRepository repository, UsuarioLogado usuarioLogado, Clock clock) {
        this.repository = repository;
        this.usuarioLogado = usuarioLogado;
        this.clock = clock;
    }

    public Reserva buscar(UUID id) {
        return repository.findDetalhadaById(id).orElseThrow(() -> new ReservaNotFoundException(id));
    }

    /** RN-03: a ação só vale nos status permitidos. */
    public static void exigirStatus(Reserva reserva, String acao, StatusReserva... permitidos) {
        if (!Arrays.asList(permitidos).contains(reserva.getStatus())) {
            throw new RegraDeNegocioException("RN-03", "Não é possível " + acao + " com a reserva em \""
                    + reserva.getStatus().rotulo() + "\".");
        }
    }

    public void registrar(Reserva reserva, String descricao) {
        reserva.registrar(descricao, agora(), usuarioLogado.atual().orElse(null));
    }

    /** Nome de quem está agindo, para as descrições do histórico. */
    public String quem() {
        return usuarioLogado.atual().map(usuario -> usuario.getNome()).orElse("usuário não identificado");
    }

    public Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /** O dia na pousada (Brasília), não o do servidor. */
    public LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
