package com.gestaocasas.application.usecase.canal;

import com.gestaocasas.application.dto.CanalRequest;
import com.gestaocasas.application.dto.CanalResponse;
import com.gestaocasas.application.mapper.CanalMapper;
import com.gestaocasas.domain.entity.Canal;
import com.gestaocasas.domain.exception.CanalNotFoundException;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.CanalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cadastro, edição, inativação e reativação de canais. */
@Component
public class SalvarCanalUseCase {

    private final CanalRepository repository;
    private final CanalMapper mapper;

    public SalvarCanalUseCase(CanalRepository repository, CanalMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public CanalResponse criar(CanalRequest request) {
        String nome = request.nome().trim();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoException("Já existe um canal chamado " + nome);
        }
        Canal canal = new Canal();
        canal.setNome(nome);
        canal.setTipo(request.tipo());
        canal.setAtivo(true);
        return mapper.toResponse(repository.saveAndFlush(canal));
    }

    @Transactional
    public CanalResponse atualizar(UUID id, CanalRequest request) {
        Canal canal = buscar(id);
        String nome = request.nome().trim();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoException("Já existe um canal chamado " + nome);
        }
        canal.setNome(nome);
        canal.setTipo(request.tipo());
        return mapper.toResponse(repository.saveAndFlush(canal));
    }

    /** Canal inativo some do formulário de nova reserva; as reservas antigas continuam com ele. */
    @Transactional
    public CanalResponse definirAtivo(UUID id, boolean ativo) {
        Canal canal = buscar(id);
        canal.setAtivo(ativo);
        return mapper.toResponse(repository.saveAndFlush(canal));
    }

    private Canal buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new CanalNotFoundException(id));
    }
}
