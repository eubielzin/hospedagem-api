package com.gestaocasas.application.usecase.bloqueio;

import com.gestaocasas.application.dto.BloqueioResponse;
import com.gestaocasas.application.mapper.BloqueioMapper;
import com.gestaocasas.domain.repository.BloqueioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class ListBloqueiosUseCase {

    // Limites que o PostgreSQL aceita em DATE (LocalDate.MIN e MAX não cabem).
    private static final LocalDate SEM_INICIO = LocalDate.of(1900, 1, 1);
    private static final LocalDate SEM_FIM = LocalDate.of(9999, 12, 31);

    private final BloqueioRepository repository;
    private final BloqueioMapper mapper;

    public ListBloqueiosUseCase(BloqueioRepository repository, BloqueioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Bloqueios ativos que cruzam [inicio, fim]; sem datas, todos os ativos. */
    @Transactional(readOnly = true)
    public List<BloqueioResponse> execute(LocalDate inicio, LocalDate fim) {
        return repository.findAtivosNoPeriodo(inicio == null ? SEM_INICIO : inicio, fim == null ? SEM_FIM : fim)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
