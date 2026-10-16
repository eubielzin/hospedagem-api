package com.gestaocasas.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * PostgreSQL dos testes de integração. Cada classe de teste recebe um banco novo e vazio, no
 * qual a aplicação roda todas as migrações; os bancos ficam em um único servidor:
 * <ul>
 *   <li>por padrão, um container {@code postgres:17-alpine} do Testcontainers (exige Docker),
 *       iniciado uma vez e reaproveitado por todas as classes;</li>
 *   <li>com a variável {@code IT_DATABASE_URL}, um PostgreSQL já em execução, para máquinas sem
 *       Docker. O usuário precisa poder criar bancos.</li>
 * </ul>
 */
final class BancoDeTeste {

    private static final String URL_EXTERNA = System.getenv("IT_DATABASE_URL");

    private static Servidor servidor;

    private BancoDeTeste() {
    }

    private record Servidor(String host, int porta, String bancoAdministrativo, String usuario, String senha) {

        String url(String banco) {
            return "jdbc:postgresql://" + host + ":" + porta + "/" + banco;
        }
    }

    record Conexao(String url, String usuario, String senha) {
    }

    /** Cria um banco vazio e aponta a aplicação para ele. Chame a partir de {@code @DynamicPropertySource}. */
    static void registrar(DynamicPropertyRegistry registry) {
        Conexao conexao = criarBanco();
        registry.add("DATABASE_URL", conexao::url);
        registry.add("DATABASE_USERNAME", conexao::usuario);
        registry.add("DATABASE_PASSWORD", conexao::senha);
    }

    /** Cria um banco vazio, sem migrações, para testes que conduzem o Flyway por conta própria. */
    static Conexao criarBanco() {
        Servidor servidor = servidor();
        String banco = "it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection conexao = DriverManager.getConnection(servidor.url(servidor.bancoAdministrativo()),
                servidor.usuario(), servidor.senha());
             Statement comando = conexao.createStatement()) {
            comando.execute("CREATE DATABASE " + banco);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível criar o banco de teste " + banco, e);
        }
        return new Conexao(servidor.url(banco), servidor.usuario(), servidor.senha());
    }

    private static synchronized Servidor servidor() {
        if (servidor == null) {
            servidor = URL_EXTERNA == null || URL_EXTERNA.isBlank() ? iniciarContainer() : externo();
        }
        return servidor;
    }

    // O container não é parado aqui: o Testcontainers o remove quando a JVM dos testes termina.
    private static Servidor iniciarContainer() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return new Servidor(container.getHost(), container.getMappedPort(5432), container.getDatabaseName(),
                container.getUsername(), container.getPassword());
    }

    private static Servidor externo() {
        // jdbc:postgresql://host:porta/banco
        URI uri = URI.create(URL_EXTERNA.substring("jdbc:".length()));
        String banco = uri.getPath() == null || uri.getPath().length() <= 1 ? "postgres" : uri.getPath().substring(1);
        return new Servidor(uri.getHost(), uri.getPort() == -1 ? 5432 : uri.getPort(), banco,
                System.getenv().getOrDefault("IT_DATABASE_USERNAME", "postgres"),
                System.getenv().getOrDefault("IT_DATABASE_PASSWORD", ""));
    }
}
