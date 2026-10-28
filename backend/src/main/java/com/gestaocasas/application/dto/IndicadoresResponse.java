package com.gestaocasas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Módulo 8. Noites contam por casa (o imóvel completo vale uma noite em cada casa) e a receita de hospedagem é
 * apropriada noite a noite. Razões (ocupação, taxas) de 0 a 1; valores em reais com 2 casas.
 */
public record IndicadoresResponse(
        LocalDate inicio,
        LocalDate fim,
        Resumo resumo,
        Resumo anterior,
        List<Mensal> mensal,
        List<OcupacaoDiaria> ocupacaoDiaria,
        List<Canal> canais,
        List<Uh> uhs,
        List<DiaSemana> diasSemana,
        List<Contagem> antecedencia,
        List<Contagem> duracao,
        List<Contagem> composicao,
        List<Contagem> origemEstados,
        List<Contagem> origemPaises,
        List<Contagem> motivos,
        List<Contagem> transportes,
        List<Contagem> regimes,
        List<FormaPagamento> formasPagamento,
        List<Contagem> despesasPorCategoria,
        List<Produto> produtos,
        List<Cliente> clientes
) {

    public record Resumo(double ocupacao, BigDecimal adr, BigDecimal revpar, int noitesVendidas,
                         BigDecimal receitaHospedagem, BigDecimal receitaConsumo, BigDecimal receitaRetida,
                         BigDecimal comissoes, BigDecimal taxasCartao, BigDecimal despesas, BigDecimal resultado,
                         int reservas, int hospedes, double estadiaMedia, double antecedenciaMedia,
                         int cancelamentos, int noShows, double taxaCancelamento, double recorrencia) {
    }

    /** mes no formato "out/25"; receitaConsumo inclui o retido e comissoes inclui as taxas de cartão. */
    public record Mensal(String mes, double ocupacao, BigDecimal adr, BigDecimal revpar, BigDecimal receitaHospedagem,
                         BigDecimal receitaConsumo, BigDecimal despesas, BigDecimal comissoes, BigDecimal resultado) {
    }

    public record OcupacaoDiaria(LocalDate data, double ocupacao) {
    }

    public record Canal(UUID canalId, String nome, String cor, int reservas, int noites, BigDecimal receita,
                        BigDecimal comissao, BigDecimal liquido, int cancelamentos) {
    }

    public record Uh(UUID uhId, String nome, double ocupacao, int noites, BigDecimal receita, BigDecimal adr,
                     BigDecimal consumo) {
    }

    public record DiaSemana(String dia, double ocupacao, BigDecimal adr) {
    }

    /** valor é contagem ou soma em reais, conforme a quebra. */
    public record Contagem(String rotulo, BigDecimal valor) {
    }

    public record FormaPagamento(String rotulo, BigDecimal valor, BigDecimal taxas) {
    }

    public record Produto(String nome, String categoria, int quantidade, BigDecimal receita) {
    }

    public record Cliente(UUID hospedeId, String nome, String origem, int estadias, int noites, BigDecimal receita) {
    }
}
