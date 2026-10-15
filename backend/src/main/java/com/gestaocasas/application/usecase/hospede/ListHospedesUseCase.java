package com.gestaocasas.application.usecase.hospede;

import com.gestaocasas.application.dto.HospedeResponse;
import com.gestaocasas.application.mapper.HospedeMapper;
import com.gestaocasas.domain.repository.HospedeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ListHospedesUseCase {

    private final HospedeRepository repository;
    private final HospedeMapper mapper;

    public ListHospedesUseCase(HospedeRepository repository, HospedeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** @param busca parte do nome ou do documento; nulo ou vazio lista todos */
    @Transactional(readOnly = true)
    public List<HospedeResponse> execute(String busca, boolean incluirInativos) {
        String termo = busca == null ? "" : busca.trim();
        return repository.buscar(escapeLike(termo), termo.replaceAll("\\D", ""), incluirInativos).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private static String escapeLike(String valor) {
        return valor.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
