package com.gestaocasas.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A V12 sobre imóveis que já existiam (como os de produção): capacidade, status e horários. */
class V12MigracaoIT {

    @Test
    void converteImoveisExistentes() {
        BancoDeTeste.Conexao conexao = BancoDeTeste.criarBanco();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(conexao.url(), conexao.usuario(),
                conexao.senha());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure().dataSource(dataSource).target("11").load().migrate();
        UUID ativo = UUID.randomUUID();
        UUID manutencao = UUID.randomUUID();
        String insert = """
                INSERT INTO imovel (id, nome, logradouro, cidade, uf, capacidade_maxima, quartos, banheiros, camas,
                                    valor_base_diaria, horario_checkin, horario_checkout, status, criado_em, atualizado_em)
                VALUES (?, ?, 'Rua', 'Cidade', 'SP', ?, 1, 1, 1, 100, '15:00', '11:00', ?, now(), now())
                """;
        jdbc.update(insert, ativo, "Casa Ativa", 6, "ATIVO");
        jdbc.update(insert, manutencao, "Casa em Obra", 3, "MANUTENCAO");

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> casa = jdbc.queryForMap("SELECT * FROM imovel WHERE id = ?", ativo);
        assertThat(casa).containsEntry("tipo", "CASA").containsEntry("status", "ATIVO")
                .containsEntry("status_governanca", "LIVRE")
                .containsEntry("capacidade_adultos", 6).containsEntry("capacidade_criancas", 0)
                .containsEntry("categoria_id", null)
                .doesNotContainKey("capacidade_maxima");
        assertThat(casa.get("horario_checkin")).hasToString("15:00:00");
        assertThat(jdbc.queryForMap("SELECT status, status_governanca FROM imovel WHERE id = ?", manutencao))
                .containsEntry("status", "ATIVO").containsEntry("status_governanca", "MANUTENCAO");
        assertThat(jdbc.queryForMap("SELECT horario_checkin, horario_checkout FROM configuracao WHERE id = 1"))
                .hasSize(2);
    }
}
