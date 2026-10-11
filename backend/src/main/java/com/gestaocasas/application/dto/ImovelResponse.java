package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusImovel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ImovelResponse(
        UUID id,
        String nome,
        String descricao,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Integer capacidadeMaxima,
        Integer quartos,
        Integer banheiros,
        Integer camas,
        BigDecimal valorBaseDiaria,
        LocalTime horarioCheckin,
        LocalTime horarioCheckout,
        StatusImovel status,
        Set<String> comodidades,
        List<ImovelFotoResponse> fotos,
        Instant criadoEm,
        Instant atualizadoEm
) {
}
