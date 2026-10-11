package com.gestaocasas.infrastructure.storage;

import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.storage.StorageUploadResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercita o adaptador contra um servidor HTTP local que simula o Supabase Storage. */
class SupabaseStorageAdapterTest {

    private static final String CHAVE = "sb_secret_chave-de-teste";

    private final String objectPath = UUID.randomUUID() + "/" + UUID.randomUUID() + ".png";
    private final AtomicReference<Recebido> recebido = new AtomicReference<>();

    private HttpServer server;
    private volatile int status = 200;
    private volatile String corpoResposta = "{}";
    private volatile long atrasoMs = 0;

    private record Recebido(String metodo, String caminho, String apikey, String authorization,
                            String contentType, String upsert, byte[] corpo) {
    }

    @BeforeEach
    void iniciarServidor() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::responder);
        server.start();
    }

    @AfterEach
    void pararServidor() {
        server.stop(0);
    }

    private void responder(HttpExchange exchange) throws IOException {
        recebido.set(new Recebido(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("apikey"),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("x-upsert"),
                exchange.getRequestBody().readAllBytes()));
        try {
            Thread.sleep(atrasoMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] corpo = corpoResposta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, corpo.length);
        exchange.getResponseBody().write(corpo);
        exchange.close();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private SupabaseStorageAdapter adapter(String url, String chave, Duration leitura) {
        return new SupabaseStorageAdapter(
                new SupabaseStorageProperties(url, "imoveis", chave, Duration.ofSeconds(2), leitura));
    }

    private SupabaseStorageAdapter adapter() {
        return adapter(baseUrl() + "/", CHAVE, Duration.ofSeconds(5));
    }

    @Test
    void enviaArquivoAutenticadoSemSobrescrever() {
        corpoResposta = "{\"Key\":\"imoveis/" + objectPath + "\",\"Id\":\"abc\"}";

        StorageUploadResult resultado = adapter().upload(objectPath, new byte[]{1, 2, 3}, "image/png");

        Recebido r = recebido.get();
        assertThat(r.metodo()).isEqualTo("POST");
        assertThat(r.caminho()).isEqualTo("/storage/v1/object/imoveis/" + objectPath);
        assertThat(r.apikey()).isEqualTo(CHAVE);
        assertThat(r.authorization()).isEqualTo("Bearer " + CHAVE);
        assertThat(r.contentType()).isEqualTo("image/png");
        assertThat(r.upsert()).isEqualTo("false");
        assertThat(r.corpo()).containsExactly(1, 2, 3);
        assertThat(resultado.objectPath()).isEqualTo(objectPath);
        assertThat(resultado.publicUrl())
                .isEqualTo(baseUrl() + "/storage/v1/object/public/imoveis/" + objectPath);
    }

    @Test
    void erroDoSupabaseNoUploadViraStorageExceptionSemExporAChave() {
        status = 403;
        corpoResposta = "{\"statusCode\":\"403\",\"error\":\"Unauthorized\",\"message\":\"new row violates policy\"}";

        assertThatThrownBy(() -> adapter().upload(objectPath, new byte[]{1}, "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining(CHAVE);
    }

    @Test
    void objetoJaExistenteNoUploadViraStorageException() {
        status = 409;
        corpoResposta = "{\"statusCode\":\"409\",\"error\":\"Duplicate\",\"message\":\"The resource already exists\"}";

        assertThatThrownBy(() -> adapter().upload(objectPath, new byte[]{1}, "image/png"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void respostaDeUploadSemAChaveEsperadaERejeitada() {
        corpoResposta = "{\"ok\":true}";

        assertThatThrownBy(() -> adapter().upload(objectPath, new byte[]{1}, "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("inesperada");
    }

    @Test
    void timeoutDeLeituraViraStorageException() {
        atrasoMs = 1500;

        assertThatThrownBy(() -> adapter(baseUrl(), CHAVE, Duration.ofMillis(200))
                .upload(objectPath, new byte[]{1}, "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("comunicação");
    }

    @Test
    void servidorInacessivelViraStorageException() {
        String url = baseUrl();
        server.stop(0);

        assertThatThrownBy(() -> adapter(url, CHAVE, Duration.ofSeconds(1)).delete(objectPath))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void excluiObjeto() {
        corpoResposta = "{\"message\":\"Successfully deleted\"}";

        adapter().delete(objectPath);

        assertThat(recebido.get().metodo()).isEqualTo("DELETE");
        assertThat(recebido.get().caminho()).isEqualTo("/storage/v1/object/imoveis/" + objectPath);
        assertThat(recebido.get().apikey()).isEqualTo(CHAVE);
    }

    @Test
    void excluirObjetoInexistenteNaoEErro() {
        status = 404;
        adapter().delete(objectPath);

        status = 400;
        corpoResposta = "{\"statusCode\":\"404\",\"error\":\"not_found\",\"message\":\"Object not found\"}";
        adapter().delete(objectPath);
    }

    @Test
    void erroDoSupabaseNaExclusaoViraStorageException() {
        status = 500;

        assertThatThrownBy(() -> adapter().delete(objectPath)).isInstanceOf(StorageException.class);
    }

    @Test
    void recusaCaminhosQueNaoForamGeradosPelaAplicacao() {
        for (String caminho : new String[]{"../segredo.png", "foto.png", UUID.randomUUID() + "/../x.png",
                UUID.randomUUID() + "/" + UUID.randomUUID() + ".svg", "imoveis/" + objectPath, null}) {
            assertThatThrownBy(() -> adapter().upload(caminho, new byte[]{1}, "image/png"))
                    .isInstanceOf(StorageException.class);
            assertThatThrownBy(() -> adapter().delete(caminho)).isInstanceOf(StorageException.class);
        }
        assertThat(recebido.get()).isNull();
    }

    @Test
    void semConfiguracaoNaoFazChamadaExterna() {
        assertThatThrownBy(() -> adapter(baseUrl(), "  ", Duration.ofSeconds(1))
                .upload(objectPath, new byte[]{1}, "image/png"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("não configurado");
        assertThat(recebido.get()).isNull();
    }

    @Test
    void toStringDasPropriedadesNaoRevelaAChave() {
        assertThat(new SupabaseStorageProperties(baseUrl(), "imoveis", CHAVE, null, null).toString())
                .doesNotContain(CHAVE);
    }
}
