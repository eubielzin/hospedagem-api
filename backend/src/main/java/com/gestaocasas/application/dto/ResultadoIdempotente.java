package com.gestaocasas.application.dto;

import java.util.function.Function;

/**
 * Resultado de uma operação protegida por chave de idempotência.
 *
 * @param repetida {@code true} quando a operação já tinha sido executada com a mesma chave e o
 *                 valor é o resultado daquela primeira execução
 */
public record ResultadoIdempotente<T>(T valor, boolean repetida) {

    public static <T> ResultadoIdempotente<T> novo(T valor) {
        return new ResultadoIdempotente<>(valor, false);
    }

    public static <T> ResultadoIdempotente<T> repetido(T valor) {
        return new ResultadoIdempotente<>(valor, true);
    }

    public <R> ResultadoIdempotente<R> map(Function<T, R> conversao) {
        return new ResultadoIdempotente<>(conversao.apply(valor), repetida);
    }
}
