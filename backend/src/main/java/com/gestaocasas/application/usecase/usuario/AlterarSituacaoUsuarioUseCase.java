package com.gestaocasas.application.usecase.usuario;

import com.gestaocasas.application.dto.UsuarioResponse;
import com.gestaocasas.application.mapper.UsuarioMapper;
import com.gestaocasas.domain.entity.Usuario;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.exception.UsuarioNotFoundException;
import com.gestaocasas.domain.repository.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inativa ou reativa um usuário. O inativado perde o acesso na hora: o token dele passa a ser
 * recusado na próxima requisição. Ninguém inativa a si mesmo, o que também garante que sempre
 * reste ao menos um usuário ativo.
 */
@Component
public class AlterarSituacaoUsuarioUseCase {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;

    public AlterarSituacaoUsuarioUseCase(UsuarioRepository repository, UsuarioMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public UsuarioResponse inativar(UUID id, UUID solicitanteId) {
        if (id.equals(solicitanteId)) {
            throw new RegraDeNegocioException("Você não pode inativar o próprio usuário");
        }
        return definir(id, false);
    }

    @Transactional
    public UsuarioResponse reativar(UUID id) {
        return definir(id, true);
    }

    private UsuarioResponse definir(UUID id, boolean ativo) {
        Usuario usuario = repository.findById(id).orElseThrow(() -> new UsuarioNotFoundException(id));
        usuario.setAtivo(ativo);
        return mapper.toResponse(repository.saveAndFlush(usuario));
    }
}
