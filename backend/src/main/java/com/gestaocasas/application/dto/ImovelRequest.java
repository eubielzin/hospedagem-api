package com.gestaocasas.application.dto;

import com.gestaocasas.application.validation.ImovelValido;
import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Campos novos da UH (tipo, categoriaId, capacidades) são opcionais: ausentes na edição, ficam como estão,
 * para o front que ainda não os conhece não apagá-los. Endereço, cidade e UF só são exigidos de casas;
 * o imóvel completo sem endereço recebe o da pousada (o da primeira casa cadastrada).
 */
@ImovelValido
public record ImovelRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres")
        String nome,

        @Size(max = 5000, message = "Descrição deve ter no máximo 5000 caracteres")
        String descricao,

        @Size(max = 200, message = "Logradouro deve ter no máximo 200 caracteres")
        String logradouro,

        @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        String numero,

        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        String complemento,

        @Size(max = 100, message = "Bairro deve ter no máximo 100 caracteres")
        String bairro,

        @Size(max = 100, message = "Cidade deve ter no máximo 100 caracteres")
        String cidade,

        @Pattern(regexp = "[A-Za-z]{2}", message = "UF deve ter 2 letras")
        String uf,

        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido")
        String cep,

        TipoUh tipo,

        UUID categoriaId,

        @Min(value = 1, message = "A UH precisa comportar ao menos 1 adulto")
        Integer capacidadeAdultos,

        @Min(value = 0, message = "Capacidade de crianças não pode ser negativa")
        Integer capacidadeCriancas,

        /** Forma antiga, só com o total: usada quando capacidadeAdultos não vem. */
        @Positive(message = "Capacidade máxima deve ser maior que zero")
        Integer capacidadeMaxima,

        @NotNull(message = "Quantidade de quartos é obrigatória")
        @PositiveOrZero(message = "Quantidade de quartos não pode ser negativa")
        Integer quartos,

        @NotNull(message = "Quantidade de banheiros é obrigatória")
        @PositiveOrZero(message = "Quantidade de banheiros não pode ser negativa")
        Integer banheiros,

        @NotNull(message = "Quantidade de camas é obrigatória")
        @PositiveOrZero(message = "Quantidade de camas não pode ser negativa")
        Integer camas,

        @NotNull(message = "Valor-base da diária é obrigatório")
        @PositiveOrZero(message = "Valor-base da diária não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Valor-base da diária deve ter no máximo 2 casas decimais")
        BigDecimal valorBaseDiaria,

        /** Nulo: vale o horário padrão da configuração. */
        LocalTime horarioCheckin,

        LocalTime horarioCheckout,

        StatusImovel status,

        @Size(max = 50, message = "Informe no máximo 50 comodidades")
        Set<@NotBlank(message = "Comodidade não pode ser vazia")
            @Size(max = 80, message = "Comodidade deve ter no máximo 80 caracteres") String> comodidades
) {
}
