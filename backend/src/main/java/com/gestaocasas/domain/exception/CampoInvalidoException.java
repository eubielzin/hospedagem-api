package com.gestaocasas.domain.exception;

/** Dado da requisição recusado por uma regra que a validação do DTO não alcança. Responde 400 no campo. */
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String message) {
        super(message);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
