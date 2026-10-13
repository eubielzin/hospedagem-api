package com.gestaocasas.domain.exception;

public class EstoqueInsuficienteException extends RegraDeNegocioException {

    public EstoqueInsuficienteException(String produto, int disponivel, int solicitado) {
        super("Estoque insuficiente de '" + produto + "' neste imóvel: disponível " + disponivel
                + ", solicitado " + solicitado);
    }
}
