package com.gestaocasas.infrastructure.storage;

import com.gestaocasas.domain.exception.StorageException;
import com.gestaocasas.domain.storage.StoragePort;
import com.gestaocasas.domain.storage.StorageUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente HTTP do Supabase Storage. A chave secreta fica somente aqui, no servidor.
 */
@Component
public class SupabaseStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageAdapter.class);

    // {imovelId}/{imagemId}.{extensao}: qualquer outro formato de caminho é recusado antes de sair da aplicação.
    private static final Pattern CAMINHO_VALIDO = Pattern.compile(
            "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final SupabaseStorageProperties properties;
    private final String baseUrl;
    private final RestClient restClient;

    public SupabaseStorageAdapter(SupabaseStorageProperties properties) {
        this.properties = properties;
        this.baseUrl = properties.url() == null ? "" : properties.url().strip().replaceAll("/+$", "");

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public StorageUploadResult upload(String objectPath, byte[] content, String contentType) {
        validar(objectPath);
        Map<?, ?> resposta;
        try {
            resposta = restClient.post()
                    .uri(objectUrl(objectPath))
                    .headers(this::autenticar)
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(content)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            log.error("Supabase Storage recusou o upload de {}: HTTP {} {}", objectPath,
                    ex.getStatusCode().value(), resumo(ex.getResponseBodyAsString()));
            throw new StorageException("Upload recusado pelo armazenamento (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.error("Falha de comunicação com o Supabase Storage no upload de {}: {}", objectPath, ex.getMessage());
            throw new StorageException("Falha de comunicação com o armazenamento", ex);
        }

        // Resposta esperada: {"Key": "<bucket>/<caminho>", "Id": "..."}
        Object key = resposta == null ? null : resposta.get("Key");
        if (!(key instanceof String chave) || !chave.endsWith(objectPath)) {
            log.error("Resposta inesperada do Supabase Storage no upload de {}", objectPath);
            throw new StorageException("Resposta inesperada do armazenamento");
        }
        return new StorageUploadResult(objectPath, getPublicUrl(objectPath));
    }

    @Override
    public void delete(String objectPath) {
        validar(objectPath);
        try {
            restClient.delete()
                    .uri(objectUrl(objectPath))
                    .headers(this::autenticar)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (objetoInexistente(ex)) {
                log.info("Objeto {} já não existia no Supabase Storage", objectPath);
                return;
            }
            log.error("Supabase Storage recusou a exclusão de {}: HTTP {} {}", objectPath,
                    ex.getStatusCode().value(), resumo(ex.getResponseBodyAsString()));
            throw new StorageException("Exclusão recusada pelo armazenamento (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.error("Falha de comunicação com o Supabase Storage na exclusão de {}: {}", objectPath, ex.getMessage());
            throw new StorageException("Falha de comunicação com o armazenamento", ex);
        }
    }

    @Override
    public String getPublicUrl(String objectPath) {
        return baseUrl + "/storage/v1/object/public/" + properties.bucket() + "/" + objectPath;
    }

    private void validar(String objectPath) {
        if (!properties.configurado()) {
            log.error("Supabase Storage não configurado: defina SUPABASE_URL, SUPABASE_STORAGE_BUCKET e SUPABASE_SECRET_KEY");
            throw new StorageException("Armazenamento de imagens não configurado");
        }
        if (objectPath == null || !CAMINHO_VALIDO.matcher(objectPath).matches()) {
            throw new StorageException("Caminho de objeto inválido");
        }
    }

    private String objectUrl(String objectPath) {
        return baseUrl + "/storage/v1/object/" + properties.bucket() + "/" + objectPath;
    }

    // O gateway do Supabase exige "apikey"; "Authorization" com o mesmo valor atende tanto as
    // secret keys novas (sb_secret_...) quanto a chave JWT legada service_role.
    private void autenticar(HttpHeaders headers) {
        String chave = properties.secretKey().strip();
        headers.set("apikey", chave);
        headers.setBearerAuth(chave);
    }

    // O Storage responde 404, ou 400 com "statusCode": "404" no corpo, quando o objeto não existe.
    private static boolean objetoInexistente(RestClientResponseException ex) {
        if (ex.getStatusCode().value() == 404) {
            return true;
        }
        String corpo = ex.getResponseBodyAsString();
        return ex.getStatusCode().value() == 400 && (corpo.contains("\"404\"") || corpo.contains("not_found"));
    }

    private static String resumo(String corpo) {
        String limpo = corpo == null ? "" : corpo.replaceAll("\\s+", " ");
        return limpo.length() > 300 ? limpo.substring(0, 300) + "..." : limpo;
    }
}
