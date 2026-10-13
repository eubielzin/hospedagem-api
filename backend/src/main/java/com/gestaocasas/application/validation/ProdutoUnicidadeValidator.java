package com.gestaocasas.application.validation;

import com.gestaocasas.application.dto.ProdutoRequest;
import com.gestaocasas.application.mapper.ProdutoMapper;
import com.gestaocasas.domain.exception.ConflitoException;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Recusa código e código de barras já usados por outro produto, com mensagem que diz qual campo
 * colidiu. Os índices únicos do banco continuam sendo a garantia em caso de requisições simultâneas.
 */
@Component
public class ProdutoUnicidadeValidator {

    private final ProdutoRepository repository;

    public ProdutoUnicidadeValidator(ProdutoRepository repository) {
        this.repository = repository;
    }

    /** @param idAtual produto que está sendo alterado, ou {@code null} em um cadastro novo */
    public void validar(ProdutoRequest request, UUID idAtual) {
        String codigo = ProdutoMapper.texto(request.codigo());
        boolean codigoEmUso = idAtual == null
                ? repository.existsByCodigoIgnoreCase(codigo)
                : repository.existsByCodigoIgnoreCaseAndIdNot(codigo, idAtual);
        if (codigoEmUso) {
            throw new ConflitoException("Já existe um produto com o código '" + codigo + "'");
        }

        String codigoBarras = ProdutoMapper.texto(request.codigoBarras());
        if (codigoBarras == null) {
            return;
        }
        boolean codigoBarrasEmUso = idAtual == null
                ? repository.existsByCodigoBarras(codigoBarras)
                : repository.existsByCodigoBarrasAndIdNot(codigoBarras, idAtual);
        if (codigoBarrasEmUso) {
            throw new ConflitoException("Já existe um produto com o código de barras '" + codigoBarras + "'");
        }
    }
}
