package com.gestaocasas.application.validation;

import com.gestaocasas.domain.enums.FormatoImagem;
import com.gestaocasas.domain.exception.ArquivoInvalidoException;
import com.gestaocasas.domain.exception.ArquivoInvalidoException.Motivo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

/**
 * Valida uma imagem pelo conteúdo, sem confiar no Content-Type nem no nome enviados pelo cliente.
 * Lê apenas os cabeçalhos do arquivo: não decodifica a imagem, o que mantém o custo baixo no Render.
 */
@Component
public class ImagemValidator {

    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final long tamanhoMaximoBytes;
    private final int dimensaoMaxima;
    private final long pixelsMaximos;

    public ImagemValidator(@Value("${app.fotos.tamanho-maximo:5MB}") DataSize tamanhoMaximo,
                           @Value("${app.fotos.dimensao-maxima:8000}") int dimensaoMaxima,
                           @Value("${app.fotos.pixels-maximos:40000000}") long pixelsMaximos) {
        this.tamanhoMaximoBytes = tamanhoMaximo.toBytes();
        this.dimensaoMaxima = dimensaoMaxima;
        this.pixelsMaximos = pixelsMaximos;
    }

    public ImagemValidada validar(byte[] conteudo, String contentTypeInformado) {
        if (conteudo == null || conteudo.length == 0) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO, "Arquivo vazio");
        }
        if (conteudo.length > tamanhoMaximoBytes) {
            throw new ArquivoInvalidoException(Motivo.MUITO_GRANDE,
                    "Arquivo excede o tamanho máximo de " + tamanhoMaximoBytes / (1024 * 1024) + " MB");
        }

        FormatoImagem informado = FormatoImagem.porMimeType(tipoBase(contentTypeInformado))
                .orElseThrow(() -> new ArquivoInvalidoException(Motivo.TIPO_NAO_SUPORTADO,
                        "Tipo de arquivo não permitido. Envie JPEG, PNG ou WebP"));

        FormatoImagem real = detectarFormato(conteudo);
        if (real == null) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO,
                    "O conteúdo do arquivo não é uma imagem JPEG, PNG ou WebP");
        }
        if (real != informado) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO,
                    "O conteúdo do arquivo não corresponde ao tipo informado");
        }

        int[] dimensoes = switch (real) {
            case PNG -> dimensoesPng(conteudo);
            case JPEG -> dimensoesJpeg(conteudo);
            case WEBP -> dimensoesWebp(conteudo);
        };
        if (dimensoes == null || dimensoes[0] <= 0 || dimensoes[1] <= 0) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO, "Imagem corrompida ou incompleta");
        }
        if (dimensoes[0] > dimensaoMaxima || dimensoes[1] > dimensaoMaxima
                || (long) dimensoes[0] * dimensoes[1] > pixelsMaximos) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO,
                    "Imagem com dimensões acima do permitido (máximo de " + dimensaoMaxima + " px por lado)");
        }
        return new ImagemValidada(real, dimensoes[0], dimensoes[1]);
    }

    private static String tipoBase(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametros = contentType.indexOf(';');
        return (parametros < 0 ? contentType : contentType.substring(0, parametros)).strip();
    }

    private static FormatoImagem detectarFormato(byte[] c) {
        if (comecaCom(c, 0, ASSINATURA_PNG)) {
            return FormatoImagem.PNG;
        }
        if (c.length >= 3 && u8(c, 0) == 0xFF && u8(c, 1) == 0xD8 && u8(c, 2) == 0xFF) {
            return FormatoImagem.JPEG;
        }
        if (comecaCom(c, 0, ascii("RIFF")) && comecaCom(c, 8, ascii("WEBP"))) {
            return FormatoImagem.WEBP;
        }
        return null;
    }

    // Largura e altura ficam no bloco IHDR, logo após a assinatura.
    private static int[] dimensoesPng(byte[] c) {
        if (c.length < 24 || !comecaCom(c, 12, ascii("IHDR"))) {
            return null;
        }
        return new int[]{(int) u32be(c, 16), (int) u32be(c, 20)};
    }

    // Percorre os segmentos até o marcador SOFn, que traz altura e largura.
    private static int[] dimensoesJpeg(byte[] c) {
        int i = 2;
        while (i + 4 <= c.length) {
            if (u8(c, i) != 0xFF) {
                return null;
            }
            int marcador = u8(c, i + 1);
            if (marcador == 0xFF) {
                i++;
                continue;
            }
            if (marcador == 0xD8 || marcador == 0x01 || (marcador >= 0xD0 && marcador <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marcador == 0xD9 || marcador == 0xDA) {
                return null;
            }
            boolean sof = marcador >= 0xC0 && marcador <= 0xCF
                    && marcador != 0xC4 && marcador != 0xC8 && marcador != 0xCC;
            if (sof) {
                if (i + 9 > c.length) {
                    return null;
                }
                return new int[]{u16be(c, i + 7), u16be(c, i + 5)};
            }
            int tamanho = u16be(c, i + 2);
            if (tamanho < 2) {
                return null;
            }
            i += 2 + tamanho;
        }
        return null;
    }

    private static int[] dimensoesWebp(byte[] c) {
        if (c.length < 30) {
            return null;
        }
        if (comecaCom(c, 12, ascii("VP8 "))) {
            if (u8(c, 23) != 0x9D || u8(c, 24) != 0x01 || u8(c, 25) != 0x2A) {
                return null;
            }
            return new int[]{u16le(c, 26) & 0x3FFF, u16le(c, 28) & 0x3FFF};
        }
        if (comecaCom(c, 12, ascii("VP8L"))) {
            if (u8(c, 20) != 0x2F) {
                return null;
            }
            long bits = u8(c, 21) | (long) u8(c, 22) << 8 | (long) u8(c, 23) << 16 | (long) u8(c, 24) << 24;
            return new int[]{(int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1};
        }
        if (comecaCom(c, 12, ascii("VP8X"))) {
            return new int[]{u24le(c, 24) + 1, u24le(c, 27) + 1};
        }
        return null;
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean comecaCom(byte[] c, int inicio, byte[] prefixo) {
        if (c.length < inicio + prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (c[inicio + i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] c, int i) {
        return c[i] & 0xFF;
    }

    private static int u16be(byte[] c, int i) {
        return u8(c, i) << 8 | u8(c, i + 1);
    }

    private static int u16le(byte[] c, int i) {
        return u8(c, i) | u8(c, i + 1) << 8;
    }

    private static int u24le(byte[] c, int i) {
        return u8(c, i) | u8(c, i + 1) << 8 | u8(c, i + 2) << 16;
    }

    private static long u32be(byte[] c, int i) {
        return (long) u8(c, i) << 24 | u8(c, i + 1) << 16 | u8(c, i + 2) << 8 | u8(c, i + 3);
    }
}
