package com.gestaocasas.domain.exception;

import java.util.UUID;

public class ProdutoNotFoundException extends ResourceNotFoundException {

    public ProdutoNotFoundException(UUID id) {
        super("Produto não encontrado: " + id);
    }
}
