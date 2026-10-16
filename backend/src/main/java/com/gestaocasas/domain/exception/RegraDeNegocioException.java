package com.gestaocasas.domain.exception;

/**
 * A requisição é bem formada, mas a operação não é permitida no estado atual dos dados. O código, quando
 * houver, é o da regra do levantamento (RN-xx ou RF-xx) e vai no campo {@code codigo} da resposta; a
 * mensagem não o repete.
 */
public class RegraDeNegocioException extends RuntimeException {

    private final String codigo;

    public RegraDeNegocioException(String message) {
        this(null, message);
    }

    public RegraDeNegocioException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
