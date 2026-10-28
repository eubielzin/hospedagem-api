package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.Usuario;

import java.util.Optional;

/** Quem fez a ação, para o histórico e os registros (bloqueios, pagamentos). Vazio fora de uma requisição com token. */
public interface UsuarioLogado {

    Optional<Usuario> atual();
}
