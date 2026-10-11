package com.gestaocasas.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.storage.supabase")
public record SupabaseStorageProperties(
        String url,
        String bucket,
        String secretKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout
) {

    public boolean configurado() {
        return temTexto(url) && temTexto(bucket) && temTexto(secretKey);
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    // A chave nunca deve aparecer em logs ou mensagens de erro.
    @Override
    public String toString() {
        return "SupabaseStorageProperties[url=" + url + ", bucket=" + bucket + ", secretKey=***]";
    }
}
