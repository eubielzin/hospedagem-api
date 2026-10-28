package com.gestaocasas.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A V13 sobre reservas que já existiam (como as de produção): encerramento, regime e histórico. */
class V13MigracaoIT {

    @Test
    void converteReservasExistentes() {
        BancoDeTeste.Conexao conexao = BancoDeTeste.criarBanco();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(conexao.url(), conexao.usuario(),
                conexao.senha());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("12").load().migrate();
        UUID imovel = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO imovel (id, nome, logradouro, cidade, uf, quartos, banheiros, camas, valor_base_diaria,
                                    status, tipo, capacidade_adultos, capacidade_criancas, status_governanca,
                                    criado_em, atualizado_em)
                VALUES (?, 'Casa', 'Rua', 'Cidade', 'MA', 1, 1, 1, 0, 'ATIVO', 'CASA', 2, 0, 'LIVRE', now(), now())
                """, imovel);
        UUID hospede = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO hospede (id, nome, tipo_documento, numero_documento, nacionalidade, ativo, criado_em,
                                     atualizado_em)
                VALUES (?, 'Ana', 'CPF', '', 'Brasileira', true, now(), now())
                """, hospede);
        UUID canal = jdbc.queryForObject("SELECT id FROM canal WHERE tipo = 'DIRETO'", UUID.class);
        String insert = """
                INSERT INTO reserva (id, codigo, imovel_id, hospede_id, canal_id, checkin, checkout, adultos, criancas,
                                     valor_total, status, motivo_cancelamento, checkin_realizado_em, cancelada_em,
                                     criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, ?, '2026-11-01', '2026-11-03', 2, 0, 500, ?, ?, ?, ?, now(), now())
                """;
        UUID cancelada = UUID.randomUUID();
        UUID hospedada = UUID.randomUUID();
        // 01:00 UTC de 20/10 ainda é 19/10 em Brasília.
        java.sql.Timestamp canceladaEm = java.sql.Timestamp.from(java.time.Instant.parse("2026-10-20T01:00:00Z"));
        jdbc.update(insert, cancelada, "CM-1001", imovel, hospede, canal, "CANCELADA", "Desistiu", null, canceladaEm);
        jdbc.update(insert, hospedada, "CM-1002", imovel, hospede, canal, "HOSPEDADO", null,
                java.sql.Timestamp.from(java.time.Instant.now()), null);

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> encerrada = jdbc.queryForMap("SELECT * FROM reserva WHERE id = ?", cancelada);
        assertThat(encerrada).containsEntry("encerramento_motivo", "Desistiu")
                .containsEntry("regime_pensao", "SEM_REFEICOES")
                .containsEntry("pendencia_autorizada", false)
                .doesNotContainKey("motivo_cancelamento");
        assertThat(((java.sql.Date) encerrada.get("encerramento_data")).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(encerrada.get("encerramento_multa")).hasToString("0.00");
        assertThat(jdbc.queryForList("SELECT descricao FROM reserva_evento WHERE reserva_id = ? ORDER BY data_hora",
                String.class, cancelada)).containsExactlyInAnyOrder("Reserva criada", "Cancelada: Desistiu");
        assertThat(jdbc.queryForList("SELECT descricao FROM reserva_evento WHERE reserva_id = ? ORDER BY data_hora",
                String.class, hospedada)).containsExactlyInAnyOrder("Reserva criada", "Check-in realizado");
        assertThat(jdbc.queryForMap("SELECT encerramento_data FROM reserva WHERE id = ?", hospedada))
                .containsEntry("encerramento_data", null);
    }
}
