package com.gestaocasas.application.service;

import com.gestaocasas.application.dto.ResultadoIdempotente;
import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import com.gestaocasas.domain.enums.OperacaoIdempotente;
import com.gestaocasas.domain.repository.MovimentacaoEstoqueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Executa uma movimentação de estoque no máximo uma vez por chave de idempotência. */
@Component
public class MovimentacaoIdempotente {

    private final Idempotencia idempotencia;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    public MovimentacaoIdempotente(Idempotencia idempotencia, MovimentacaoEstoqueRepository movimentacaoRepository) {
        this.idempotencia = idempotencia;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    /** @param chave chave enviada pelo cliente; {@code null} executa a operação sem proteção contra repetição */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoIdempotente<MovimentacaoEstoque> executar(OperacaoIdempotente operacao, String chave,
                                                              String conteudo, Supplier<MovimentacaoEstoque> acao) {
        if (chave == null) {
            return ResultadoIdempotente.novo(acao.get());
        }
        Optional<UUID> anterior = idempotencia.iniciar(operacao, chave, conteudo);
        if (anterior.isPresent()) {
            return ResultadoIdempotente.repetido(movimentacaoRepository.findById(anterior.get())
                    .orElseThrow(() -> new IllegalStateException("Movimentação da chave não encontrada")));
        }
        MovimentacaoEstoque movimentacao = acao.get();
        idempotencia.concluir(operacao, chave, movimentacao.getId());
        return ResultadoIdempotente.novo(movimentacao);
    }
}
