package com.gestaocasas.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gestaocasas.domain.enums.FormaPagamento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/** Horários em "HH:mm". */
public record ConfiguracaoRequest(
        @NotBlank(message = "Informe o nome da pousada")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String nomePousada,

        @NotBlank(message = "Informe a cidade")
        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String cidade,

        @NotNull(message = "Informe o horário de check-in")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horarioCheckin,

        @NotNull(message = "Informe o horário de check-out")
        @JsonFormat(pattern = "HH:mm")
        LocalTime horarioCheckout,

        @NotNull(message = "Informe a idade limite de criança")
        @Min(value = 0, message = "A idade limite vai de 0 a 17")
        @Max(value = 17, message = "A idade limite vai de 0 a 17")
        Integer idadeLimiteCrianca,

        @NotBlank(message = "Informe a política de cancelamento")
        @Size(max = 2000, message = "Política deve ter no máximo 2000 caracteres")
        String politicaCancelamento,

        @NotEmpty(message = "Informe ao menos uma forma de pagamento")
        List<@NotNull(message = "Forma de pagamento inválida") FormaPagamento> formasPagamento
) {
}
