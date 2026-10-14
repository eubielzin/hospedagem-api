package com.gestaocasas.domain.exception;

import java.util.UUID;

public class UsuarioNotFoundException extends ResourceNotFoundException {

    public UsuarioNotFoundException(UUID id) {
        super("Usuário não encontrado: " + id);
    }
}
