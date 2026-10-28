package com.gestaocasas.application.dto;

import com.gestaocasas.domain.enums.MeioTransporte;
import com.gestaocasas.domain.enums.MotivoViagem;
import com.gestaocasas.domain.enums.RegimePensao;
import com.gestaocasas.domain.enums.StatusReserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Com os nomes já resolvidos, como a listagem da tela precisa. uhId/uhNome são do imóvel. historico só vem no
 * detalhe e nas respostas das ações (nulo nas listagens). motivoCancelamento e canceladaEm repetem o
 * encerramento das canceladas, para o front que ainda os lê.
 */
public record ReservaResponse(
        UUID id,
        String codigo,
        UUID uhId,
        String uhNome,
        UUID titularId,
        String titularNome,
        UUID canalId,
        String canalNome,
        String codigoExterno,
        LocalDate checkin,
        LocalDate checkout,
        int adultos,
        int criancas,
        BigDecimal valorTotal,
        StatusReserva status,
        RegimePensao regimePensao,
        String observacoes,
        Fnrh fnrh,
        Encerramento encerramento,
        boolean pendenciaAutorizada,
        String motivoCancelamento,
        Instant criadaEm,
        Instant checkinRealizadoEm,
        Instant checkoutRealizadoEm,
        Instant canceladaEm,
        Instant atualizadoEm,
        List<EventoHistorico> historico
) {

    public record Fnrh(String cidadeProcedencia, String cidadeDestino, MotivoViagem motivoViagem,
                       MeioTransporte meioTransporte, LocalDate emitidaEm) {
    }

    public record Encerramento(LocalDate data, String motivo, BigDecimal multa, BigDecimal valorRetido,
                               BigDecimal valorDevolver) {
    }

    /** usuario é o nome de quem fez a ação; nulo nos eventos sem usuário logado. */
    public record EventoHistorico(Instant dataHora, String descricao, String usuario) {
    }
}
