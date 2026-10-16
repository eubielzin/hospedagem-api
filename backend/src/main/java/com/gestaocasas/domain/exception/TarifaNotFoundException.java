package com.gestaocasas.domain.exception;

import java.util.UUID;

public class TarifaNotFoundException extends ResourceNotFoundException {

    public TarifaNotFoundException(UUID id) {
        super("Tarifa não encontrada: " + id);
    }
}
