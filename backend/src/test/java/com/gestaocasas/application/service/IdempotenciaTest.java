package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.ChaveIdempotencia;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.ChaveIdempotenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotenciaTest {

    private static final String CHAVE = "venda-0001";

    @Mock
    private ChaveIdempotenciaRepository repository;

    private Idempotencia idempotencia;

    @BeforeEach
    void setUp() {
        idempotencia = new Idempotencia(repository);
    }

    private String impressaoDigitalDe(String conteudo) {
        when(repository.ocupar(eq("VENDA"), eq(CHAVE), anyString())).thenReturn(1);
        idempotencia.iniciar(OperacaoIdempotente.VENDA, CHAVE, conteudo);
        ArgumentCaptor<String> impressao = ArgumentCaptor.forClass(String.class);
        verify(repository).ocupar(eq("VENDA"), eq(CHAVE), impressao.capture());
        return impressao.getValue();
    }

    private ChaveIdempotencia registro(String impressaoDigital, UUID recursoId) {
        ChaveIdempotencia registro = new ChaveIdempotencia();
        registro.setOperacao(OperacaoIdempotente.VENDA);
        registro.setChave(CHAVE);
        registro.setImpressaoDigital(impressaoDigital);
        registro.setRecursoId(recursoId);
        return registro;
    }

    @Test
    void chaveNovaMandaExecutarAOperacao() {
        when(repository.ocupar(eq("VENDA"), eq(CHAVE), anyString())).thenReturn(1);

        assertThat(idempotencia.iniciar(OperacaoIdempotente.VENDA, CHAVE, "conteudo")).isEmpty();
    }

    @Test
    void impressaoDigitalEhUmSha256Hexadecimal() {
        assertThat(impressaoDigitalDe("conteudo")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void mesmaChaveEMesmoConteudoDevolvemOResultadoAnterior() {
        String impressao = impressaoDigitalDe("conteudo");
        UUID vendaId = UUID.randomUUID();
        when(repository.ocupar(eq("VENDA"), eq(CHAVE), anyString())).thenReturn(0);
        when(repository.findByOperacaoAndChave(OperacaoIdempotente.VENDA, CHAVE))
                .thenReturn(Optional.of(registro(impressao, vendaId)));

        assertThat(idempotencia.iniciar(OperacaoIdempotente.VENDA, CHAVE, "conteudo")).contains(vendaId);
    }

    @Test
    void mesmaChaveComOutroConteudoERecusada() {
        String impressao = impressaoDigitalDe("conteudo");
        when(repository.ocupar(eq("VENDA"), eq(CHAVE), anyString())).thenReturn(0);
        when(repository.findByOperacaoAndChave(OperacaoIdempotente.VENDA, CHAVE))
                .thenReturn(Optional.of(registro(impressao, UUID.randomUUID())));

        assertThatThrownBy(() -> idempotencia.iniciar(OperacaoIdempotente.VENDA, CHAVE, "outro conteudo"))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining(CHAVE);
    }

    @Test
    void concluirGuardaOResultado() {
        ChaveIdempotencia registro = registro("x", null);
        when(repository.findByOperacaoAndChave(OperacaoIdempotente.VENDA, CHAVE)).thenReturn(Optional.of(registro));
        UUID vendaId = UUID.randomUUID();

        idempotencia.concluir(OperacaoIdempotente.VENDA, CHAVE, vendaId);

        assertThat(registro.getRecursoId()).isEqualTo(vendaId);
        verify(repository).saveAndFlush(registro);
    }
}
