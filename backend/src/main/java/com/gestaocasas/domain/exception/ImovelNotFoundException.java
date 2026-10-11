package com.gestaocasas.domain.exception;

import java.util.UUID;

public class ImovelNotFoundException extends ResourceNotFoundException {

    public ImovelNotFoundException(UUID id) {
        super("Imóvel não encontrado: " + id);
    }
}
