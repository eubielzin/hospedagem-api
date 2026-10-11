package com.gestaocasas.domain.exception;

public class ArquivoInvalidoException extends RuntimeException {

    public enum Motivo {
        CONTEUDO_INVALIDO,
        MUITO_GRANDE,
        TIPO_NAO_SUPORTADO
    }

    private final Motivo motivo;

    public ArquivoInvalidoException(Motivo motivo, String message) {
        super(message);
        this.motivo = motivo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
