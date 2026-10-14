package com.gestaocasas.domain.exception;

/**
 * E-mail ou senha incorretos, ou usuário inativo. A mensagem é a mesma nos três casos para não
 * revelar quais e-mails estão cadastrados.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos");
    }
}
