package com.gestaocasas.domain.exception;

import java.util.UUID;

public class VendaNotFoundException extends ResourceNotFoundException {

    public VendaNotFoundException(UUID id) {
        super("Venda não encontrada: " + id);
    }
}
