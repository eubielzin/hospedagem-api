package com.gestaocasas.domain.exception;

/** A requisição é bem formada, mas a operação não é permitida no estado atual dos dados. */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String message) {
        super(message);
    }
}
