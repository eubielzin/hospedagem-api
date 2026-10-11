package com.gestaocasas.application.validation;

import com.gestaocasas.domain.enums.FormatoImagem;

public record ImagemValidada(FormatoImagem formato, int largura, int altura) {
}
