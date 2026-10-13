package com.gestaocasas.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VendaRequest(
        @NotNull(message = "Imóvel é obrigatório")
        UUID imovelId,

        @NotEmpty(message = "Informe ao menos um item")
        @Size(max = 100, message = "Uma venda pode ter no máximo 100 itens")
        List<@NotNull(message = "Item não pode ser nulo") @Valid ItemVendaRequest> itens,

        @Size(max = 300, message = "Observação deve ter no máximo 300 caracteres")
        String observacao,

        // Opcional: o total que o operador viu na tela. Não é gravado; se divergir do total
        // calculado pelo backend (um preço mudou), a venda é recusada em vez de cobrar outro valor.
        @PositiveOrZero(message = "Total esperado não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Total esperado deve ter no máximo 2 casas decimais")
        BigDecimal totalEsperado
) {
}
