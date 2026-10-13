package com.gestaocasas.domain.enums;

/**
 * Situação do recebimento de uma venda. Enquanto o módulo financeiro não existir, toda venda
 * nasce e permanece PENDENTE: nenhuma rota marca RECEBIDO sem um pagamento validado.
 */
public enum StatusPagamento {
    PENDENTE,
    RECEBIDO
}
