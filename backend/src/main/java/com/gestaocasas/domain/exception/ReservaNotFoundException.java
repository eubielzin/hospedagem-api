package com.gestaocasas.domain.exception;

import java.util.UUID;

public class ReservaNotFoundException extends ResourceNotFoundException {

    public ReservaNotFoundException(UUID id) {
        super("Reserva não encontrada: " + id);
    }
}
