package com.gestaocasas.domain.exception;

import java.util.UUID;

public class BloqueioNotFoundException extends ResourceNotFoundException {

    public BloqueioNotFoundException(UUID id) {
        super("Bloqueio não encontrado: " + id);
    }
}
