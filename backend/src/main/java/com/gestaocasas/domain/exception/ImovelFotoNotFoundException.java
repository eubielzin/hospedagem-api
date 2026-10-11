package com.gestaocasas.domain.exception;

import java.util.UUID;

public class ImovelFotoNotFoundException extends ResourceNotFoundException {

    public ImovelFotoNotFoundException(UUID fotoId) {
        super("Foto não encontrada: " + fotoId);
    }
}
