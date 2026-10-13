package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.ChaveIdempotencia;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.ChaveIdempotenciaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Garante que uma requisição repetida (duplo clique, nova tentativa após timeout) não execute a
 * operação duas vezes. A chave é gravada na transação da própria operação: se a operação falhar,
 * a chave some junto e o cliente pode tentar de novo com ela.
 */
@Component
public class Idempotencia {

    private final ChaveIdempotenciaRepository repository;

    public Idempotencia(ChaveIdempotenciaRepository repository) {
        this.repository = repository;
    }

    /**
     * @param conteudo representação estável da requisição, usada para detectar a mesma chave em
     *                 uma requisição diferente
     * @return vazio se a operação deve ser executada agora (chame {@link #concluir} ao final);
     *         o id do resultado se ela já foi executada com esta chave
     * @throws ConflitoException se a chave já foi usada com outro conteúdo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> iniciar(OperacaoIdempotente operacao, String chave, String conteudo) {
        String impressaoDigital = sha256(conteudo);
        if (repository.ocupar(operacao.name(), chave, impressaoDigital) == 1) {
            return Optional.empty();
        }
        ChaveIdempotencia existente = repository.findByOperacaoAndChave(operacao, chave)
                .orElseThrow(() -> new IllegalStateException("Chave de idempotência não encontrada"));
        if (!existente.getImpressaoDigital().equals(impressaoDigital) || existente.getRecursoId() == null) {
            throw new ConflitoException("A chave de idempotência '" + chave
                    + "' já foi usada em outra requisição. Gere uma chave nova para cada operação.");
        }
        return Optional.of(existente.getRecursoId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void concluir(OperacaoIdempotente operacao, String chave, UUID recursoId) {
        ChaveIdempotencia registro = repository.findByOperacaoAndChave(operacao, chave)
                .orElseThrow(() -> new IllegalStateException("Chave de idempotência não encontrada"));
        registro.setRecursoId(recursoId);
        repository.saveAndFlush(registro);
    }

    private static String sha256(String conteudo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
