package com.gestaocasas;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Cabeçalhos mínimos de imagem: o validador lê apenas assinatura e dimensões. */
public final class ImagemFixtures {

    private ImagemFixtures() {
    }

    public static byte[] png(int largura, int altura) {
        return ByteBuffer.allocate(33)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(13).put(ascii("IHDR")).putInt(largura).putInt(altura)
                .put(new byte[]{8, 6, 0, 0, 0}).putInt(0)
                .array();
    }

    public static byte[] jpeg(int largura, int altura) {
        return ByteBuffer.allocate(64)
                .put(new byte[]{(byte) 0xFF, (byte) 0xD8})
                // APP0 (JFIF), 16 bytes de segmento
                .put(new byte[]{(byte) 0xFF, (byte) 0xE0}).putShort((short) 16).put(ascii("JFIF")).put(new byte[10])
                // SOF0: precisão, altura, largura
                .put(new byte[]{(byte) 0xFF, (byte) 0xC0}).putShort((short) 17).put((byte) 8)
                .putShort((short) altura).putShort((short) largura)
                .array();
    }

    /** WebP com perdas (bloco "VP8 "). */
    public static byte[] webp(int largura, int altura) {
        return ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .put(ascii("RIFF")).putInt(24).put(ascii("WEBP"))
                .put(ascii("VP8 ")).putInt(12)
                .put(new byte[]{0, 0, 0}).put(new byte[]{(byte) 0x9D, 0x01, 0x2A})
                .putShort((short) largura).putShort((short) altura)
                .array();
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }
}
