package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.StatusImovel;
import com.gestaocasas.domain.enums.TipoUh;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** capacidadeMaxima = adultos + crianças. Os horários já vêm com o padrão da configuração aplicado. */
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
        TipoUh tipo,
        UUID categoriaId,
        Integer capacidadeMaxima,
        Integer capacidadeAdultos,
        Integer capacidadeCriancas,
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
