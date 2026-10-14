package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.Usuario;

import java.time.Instant;

/** Gera o token de acesso entregue no login. A implementação fica na camada de infraestrutura. */
public interface EmissorDeToken {

    TokenEmitido emitir(Usuario usuario);

    record TokenEmitido(String token, Instant expiraEm) {
    }
}
