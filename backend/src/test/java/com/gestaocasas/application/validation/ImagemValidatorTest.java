package com.gestaocasas.application.validation;

import com.gestaocasas.ImagemFixtures;
import com.gestaocasas.domain.enums.FormatoImagem;
import com.gestaocasas.domain.exception.ArquivoInvalidoException;
import com.gestaocasas.domain.exception.ArquivoInvalidoException.Motivo;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagemValidatorTest {

    private final ImagemValidator validator = new ImagemValidator(DataSize.ofMegabytes(5), 8000, 40_000_000L);

    private void assertRejeita(byte[] conteudo, String contentType, Motivo motivo) {
        assertThatThrownBy(() -> validator.validar(conteudo, contentType))
                .isInstanceOfSatisfying(ArquivoInvalidoException.class,
                        ex -> assertThat(ex.getMotivo()).isEqualTo(motivo));
    }

    @Test
    void aceitaPngJpegEWebpLendoAsDimensoes() {
        assertThat(validator.validar(ImagemFixtures.png(1920, 1080), "image/png"))
                .isEqualTo(new ImagemValidada(FormatoImagem.PNG, 1920, 1080));
        assertThat(validator.validar(ImagemFixtures.jpeg(1600, 1200), "image/jpeg"))
                .isEqualTo(new ImagemValidada(FormatoImagem.JPEG, 1600, 1200));
        assertThat(validator.validar(ImagemFixtures.webp(800, 600), "image/webp"))
                .isEqualTo(new ImagemValidada(FormatoImagem.WEBP, 800, 600));
    }

    @Test
    void ignoraParametrosDoContentType() {
        assertThat(validator.validar(ImagemFixtures.png(10, 10), "IMAGE/PNG; charset=binary").formato())
                .isEqualTo(FormatoImagem.PNG);
    }

    @Test
    void rejeitaArquivoVazio() {
        assertRejeita(new byte[0], "image/png", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(null, "image/png", Motivo.CONTEUDO_INVALIDO);
    }

    @Test
    void rejeitaArquivoMaiorQue5Mb() {
        byte[] grande = Arrays.copyOf(ImagemFixtures.png(100, 100), 5 * 1024 * 1024 + 1);
        assertRejeita(grande, "image/png", Motivo.MUITO_GRANDE);

        byte[] noLimite = Arrays.copyOf(ImagemFixtures.png(100, 100), 5 * 1024 * 1024);
        assertThat(validator.validar(noLimite, "image/png").formato()).isEqualTo(FormatoImagem.PNG);
    }

    @Test
    void rejeitaTipoNaoPermitido() {
        assertRejeita("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8),
                "image/svg+xml", Motivo.TIPO_NAO_SUPORTADO);
        assertRejeita(ImagemFixtures.png(10, 10), "application/octet-stream", Motivo.TIPO_NAO_SUPORTADO);
        assertRejeita(ImagemFixtures.png(10, 10), null, Motivo.TIPO_NAO_SUPORTADO);
    }

    @Test
    void rejeitaConteudoQueNaoEImagemMesmoComContentTypePermitido() {
        assertRejeita("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8),
                "image/jpeg", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0}, "image/png", Motivo.CONTEUDO_INVALIDO);
    }

    @Test
    void rejeitaConteudoDiferenteDoTipoInformado() {
        assertRejeita(ImagemFixtures.png(10, 10), "image/jpeg", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(ImagemFixtures.jpeg(10, 10), "image/webp", Motivo.CONTEUDO_INVALIDO);
    }

    @Test
    void rejeitaImagemTruncada() {
        assertRejeita(Arrays.copyOf(ImagemFixtures.png(10, 10), 12), "image/png", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(Arrays.copyOf(ImagemFixtures.jpeg(10, 10), 6), "image/jpeg", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(Arrays.copyOf(ImagemFixtures.webp(10, 10), 16), "image/webp", Motivo.CONTEUDO_INVALIDO);
    }

    @Test
    void rejeitaDimensoesExcessivas() {
        assertRejeita(ImagemFixtures.png(8001, 100), "image/png", Motivo.CONTEUDO_INVALIDO);
        assertRejeita(ImagemFixtures.png(100, 0), "image/png", Motivo.CONTEUDO_INVALIDO);
        // 7000 x 7000 = 49 milhões de pixels: cada lado dentro do limite, o total não.
        assertRejeita(ImagemFixtures.png(7000, 7000), "image/png", Motivo.CONTEUDO_INVALIDO);
    }
}
