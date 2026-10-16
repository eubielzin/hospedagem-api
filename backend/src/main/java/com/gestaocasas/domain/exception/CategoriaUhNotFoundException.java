package com.gestaocasas.domain.exception;

import java.util.UUID;

public class CategoriaUhNotFoundException extends ResourceNotFoundException {

    public CategoriaUhNotFoundException(UUID id) {
        super("Categoria não encontrada: " + id);
    }
}
