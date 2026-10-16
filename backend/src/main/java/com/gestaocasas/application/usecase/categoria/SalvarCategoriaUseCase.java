package com.gestaocasas.application.usecase.categoria;

import com.gestaocasas.application.dto.CategoriaUhRequest;
import com.gestaocasas.application.dto.CategoriaUhResponse;
import com.gestaocasas.application.mapper.CategoriaUhMapper;
import com.gestaocasas.domain.entity.CategoriaUh;
import com.gestaocasas.domain.exception.CategoriaUhNotFoundException;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.CategoriaUhRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** RF-30: categorias das UHs. Não há exclusão (RN-15). */
@Component
public class SalvarCategoriaUseCase {

    private final CategoriaUhRepository repository;
    private final CategoriaUhMapper mapper;

    public SalvarCategoriaUseCase(CategoriaUhRepository repository, CategoriaUhMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public CategoriaUhResponse criar(CategoriaUhRequest request) {
        String nome = request.nome().trim();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new ConflitoException("Já existe uma categoria chamada " + nome);
        }
        CategoriaUh categoria = new CategoriaUh();
        categoria.setAtiva(true);
        copiar(request, nome, categoria);
        return mapper.toResponse(repository.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaUhResponse atualizar(UUID id, CategoriaUhRequest request) {
        CategoriaUh categoria = repository.findById(id).orElseThrow(() -> new CategoriaUhNotFoundException(id));
        String nome = request.nome().trim();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new ConflitoException("Já existe uma categoria chamada " + nome);
        }
        copiar(request, nome, categoria);
        return mapper.toResponse(repository.saveAndFlush(categoria));
    }

    private static void copiar(CategoriaUhRequest request, String nome, CategoriaUh categoria) {
        categoria.setNome(nome);
        categoria.setNivel(request.nivel());
        categoria.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null : request.descricao().trim());
    }
}
