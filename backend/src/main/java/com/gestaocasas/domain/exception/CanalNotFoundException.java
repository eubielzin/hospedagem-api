package com.gestaocasas.domain.exception;

import java.util.UUID;

public class CanalNotFoundException extends ResourceNotFoundException {

    public CanalNotFoundException(UUID id) {
        super("Canal não encontrado: " + id);
    }
}
