package com.gestaocasas.domain.exception;

import java.util.UUID;

public class HospedeNotFoundException extends ResourceNotFoundException {

    public HospedeNotFoundException(UUID id) {
        super("Hóspede não encontrado: " + id);
    }
}
