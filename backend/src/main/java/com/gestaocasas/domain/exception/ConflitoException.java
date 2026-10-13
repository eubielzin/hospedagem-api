package com.gestaocasas.domain.exception;

/** A operação é válida, mas colide com um registro existente (por exemplo, código duplicado). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String message) {
        super(message);
    }
}
