package com.gestaocasas.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    @Test
    void segredoAusenteOuCurtoImpedeASubida() {
        assertThatThrownBy(() -> new JwtConfig("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtConfig("curto-demais")).isInstanceOf(IllegalStateException.class);
    }
}
