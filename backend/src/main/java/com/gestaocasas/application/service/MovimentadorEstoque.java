package com.gestaocasas.application.service;

import com.gestaocasas.domain.entity.EstoqueImovel;
import com.gestaocasas.domain.entity.MovimentacaoEstoque;
import com.gestaocasas.domain.entity.Produto;
import com.gestaocasas.domain.enums.TipoMovimentacao;
import com.gestaocasas.domain.enums.TipoReferenciaMovimentacao;
import com.gestaocasas.domain.exception.EstoqueInsuficienteException;
import com.gestaocasas.domain.exception.ImovelNotFoundException;
import com.gestaocasas.domain.exception.ProdutoNotFoundException;
import com.gestaocasas.domain.exception.RegraDeNegocioException;
import com.gestaocasas.domain.repository.EstoqueImovelRepository;
import com.gestaocasas.domain.repository.ImovelRepository;
import com.gestaocasas.domain.repository.MovimentacaoEstoqueRepository;
import com.gestaocasas.domain.repository.ProdutoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Único ponto do sistema que altera a quantidade em estoque. Toda alteração bloqueia a linha de
 * saldo do produto no imóvel, confere o resultado e grava a movimentação na mesma transação do
 * caso de uso que a pediu: ou o saldo e o histórico mudam juntos, ou nada muda.
 */
@Component
public class MovimentadorEstoque {

    static final int SALDO_MAXIMO = 1_000_000_000;

    private final ImovelRepository imovelRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueImovelRepository estoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    public MovimentadorEstoque(ImovelRepository imovelRepository, ProdutoRepository produtoRepository,
                               EstoqueImovelRepository estoqueRepository,
                               MovimentacaoEstoqueRepository movimentacaoRepository) {
        this.imovelRepository = imovelRepository;
        this.produtoRepository = produtoRepository;
        this.estoqueRepository = estoqueRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    /**
     * Bloqueia o saldo do produto no imóvel até o fim da transação, criando-o zerado se faltar.
     * Quem movimenta mais de um saldo na mesma transação deve bloquear todos antes, sempre na
     * mesma ordem, para que duas operações cruzadas não fiquem esperando uma pela outra.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EstoqueImovel travar(UUID imovelId, UUID produtoId) {
        estoqueRepository.criarSeAusente(imovelId, produtoId);
        return estoqueRepository.findForUpdate(imovelId, produtoId)
                .orElseThrow(() -> new IllegalStateException("Saldo não encontrado após a criação"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MovimentacaoEstoque movimentar(UUID imovelId, UUID produtoId, TipoMovimentacao tipo, int quantidade,
                                          String motivo, TipoReferenciaMovimentacao referenciaTipo,
                                          UUID referenciaId) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }
        if (!imovelRepository.existsById(imovelId)) {
            throw new ImovelNotFoundException(imovelId);
        }
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ProdutoNotFoundException(produtoId));
        if (!produto.isControlaEstoque()) {
            throw new RegraDeNegocioException("O produto '" + produto.getNome() + "' não controla estoque");
        }
        // Produto inativo ainda pode ter o saldo corrigido, transferido ou baixado, mas não reposto.
        if (tipo == TipoMovimentacao.ENTRADA && !produto.isAtivo()) {
            throw new RegraDeNegocioException("O produto '" + produto.getNome() + "' está inativo");
        }

        EstoqueImovel estoque = travar(imovelId, produtoId);
        int saldoAnterior = estoque.getQuantidadeAtual();
        long saldoPosterior = tipo.aumentaSaldo()
                ? (long) saldoAnterior + quantidade
                : (long) saldoAnterior - quantidade;
        if (saldoPosterior < 0) {
            throw new EstoqueInsuficienteException(produto.getNome(), saldoAnterior, quantidade);
        }
        if (saldoPosterior > SALDO_MAXIMO) {
            throw new RegraDeNegocioException("O saldo de '" + produto.getNome() + "' excederia o limite de "
                    + SALDO_MAXIMO);
        }
        estoque.setQuantidadeAtual((int) saldoPosterior);

        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque();
        movimentacao.setImovel(estoque.getImovel());
        movimentacao.setProduto(produto);
        movimentacao.setTipo(tipo);
        movimentacao.setQuantidade(quantidade);
        movimentacao.setSaldoAnterior(saldoAnterior);
        movimentacao.setSaldoPosterior((int) saldoPosterior);
        movimentacao.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        movimentacao.setReferenciaTipo(referenciaTipo);
        movimentacao.setReferenciaId(referenciaId);
        // O flush leva junto a alteração do saldo: constraints do banco falham aqui, não no commit.
        return movimentacaoRepository.saveAndFlush(movimentacao);
    }
}
