package com.gestaocasas.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** Linha da busca dinâmica; {@code quantidadeDisponivel} é nula sem imóvel ou sem controle de estoque. */
public interface ProdutoBuscaView {

    UUID getId();

    String getCodigo();

    String getCodigoBarras();

    String getNome();

    String getCategoria();

    String getUnidade();

    BigDecimal getPrecoVenda();

    Boolean getControlaEstoque();

    Boolean getAtivo();

    Integer getQuantidadeDisponivel();
}
