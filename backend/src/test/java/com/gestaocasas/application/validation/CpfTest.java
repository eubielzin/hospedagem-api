package com.gestaocasas.application.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @Test
    void aceitaCpfComDigitosVerificadoresCorretos() {
        assertThat(Cpf.valido("52998224725")).isTrue();
        assertThat(Cpf.valido("11144477735")).isTrue();
    }

    @Test
    void recusaDigitoErradoTamanhoErradoERepeticao() {
        assertThat(Cpf.valido("52998224724")).isFalse();
        assertThat(Cpf.valido("5299822472")).isFalse();
        assertThat(Cpf.valido("11111111111")).isFalse();
        assertThat(Cpf.valido("529.982.247-25")).isFalse();
        assertThat(Cpf.valido(null)).isFalse();
    }
}
