package com.gestaocasas.application.usecase.bloqueio;

import com.gestaocasas.application.dto.BloqueioRequest;
import com.gestaocasas.application.dto.BloqueioResponse;
import com.gestaocasas.application.mapper.BloqueioMapper;
import com.gestaocasas.application.service.DisponibilidadeUh;
import com.gestaocasas.application.service.UsuarioLogado;
import com.gestaocasas.domain.entity.Bloqueio;
import com.gestaocasas.domain.entity.Imovel;
import com.gestaocasas.domain.exception.BloqueioNotFoundException;
import com.gestaocasas.domain.repository.BloqueioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** RF-04: bloqueio de UH. Conflita com reservas ativas e com outros bloqueios (RN-01 e RN-02). */
@Component
public class GerenciarBloqueioUseCase {

    private final BloqueioRepository repository;
    private final DisponibilidadeUh disponibilidade;
    private final UsuarioLogado usuarioLogado;
    private final BloqueioMapper mapper;
    private final Clock clock;

    public GerenciarBloqueioUseCase(BloqueioRepository repository, DisponibilidadeUh disponibilidade,
                                    UsuarioLogado usuarioLogado, BloqueioMapper mapper, Clock clock) {
        this.repository = repository;
        this.disponibilidade = disponibilidade;
        this.usuarioLogado = usuarioLogado;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public BloqueioResponse criar(BloqueioRequest request) {
        Imovel uh = disponibilidade.travar(request.uhId());
        disponibilidade.exigirAtiva(uh);
        // O fim é inclusive: o bloqueio ocupa até o dia seguinte a ele.
        disponibilidade.verificar(uh, request.inicio(), request.fim().plusDays(1), null);
        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setImovel(uh);
        bloqueio.setInicio(request.inicio());
        bloqueio.setFim(request.fim());
        bloqueio.setMotivo(request.motivo().trim());
        bloqueio.setUsuario(usuarioLogado.atual().orElse(null));
        bloqueio.setAtivo(true);
        return mapper.toResponse(repository.saveAndFlush(bloqueio));
    }

    /** RN-15: o bloqueio é encerrado, não excluído. Encerrar de novo não muda nada. */
    @Transactional
    public BloqueioResponse encerrar(UUID id) {
        Bloqueio bloqueio = repository.findDetalhadoById(id).orElseThrow(() -> new BloqueioNotFoundException(id));
        if (bloqueio.isAtivo()) {
            bloqueio.setAtivo(false);
            bloqueio.setEncerradoEm(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
            repository.saveAndFlush(bloqueio);
        }
        return mapper.toResponse(bloqueio);
    }
}
