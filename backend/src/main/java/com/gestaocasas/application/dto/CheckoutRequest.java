package com.gestaocasas.application.dto;

/** RN-05: autorizarPendencia permite o check-out com saldo em aberto. Corpo opcional (falso). */
public record CheckoutRequest(Boolean autorizarPendencia) {
}
