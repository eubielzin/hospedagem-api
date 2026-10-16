package com.gestaocasas.domain.enums;

public enum StatusImovel {
    ATIVO,
    INATIVO,
    /**
     * Só aceito na entrada, enquanto o front em produção ainda o envia: vira ATIVO com a governança em
     * MANUTENCAO. Nunca é gravado nem devolvido. Remover quando o front deixar de enviá-lo.
     */
    @Deprecated
    MANUTENCAO
}
