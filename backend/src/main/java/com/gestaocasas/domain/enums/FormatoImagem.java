package com.gestaocasas.domain.enums;

import java.util.Arrays;
import java.util.Optional;

public enum FormatoImagem {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extensao;

    FormatoImagem(String mimeType, String extensao) {
        this.mimeType = mimeType;
        this.extensao = extensao;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extensao() {
        return extensao;
    }

    public static Optional<FormatoImagem> porMimeType(String mimeType) {
        return Arrays.stream(values()).filter(formato -> formato.mimeType.equalsIgnoreCase(mimeType)).findFirst();
    }
}
