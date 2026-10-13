-- Vendas do PDV. Cada venda pertence a um imóvel, e o estoque é baixado somente dele.
CREATE TABLE venda (
    id                  UUID           PRIMARY KEY,
    imovel_id           UUID           NOT NULL REFERENCES imovel (id),
    tipo                VARCHAR(20)    NOT NULL,
    -- Ponto de integração: o módulo de reservas ainda não existe, então não há chave estrangeira
    -- e a API só registra vendas avulsas. A coluna fica reservada para quando ele existir.
    reserva_id          UUID,
    status              VARCHAR(20)    NOT NULL,
    -- Ponto de integração com o financeiro, que ainda não existe: nenhuma rota marca RECEBIDO.
    pagamento_status    VARCHAR(20)    NOT NULL,
    total               NUMERIC(12, 2) NOT NULL,
    observacao          VARCHAR(300),
    motivo_cancelamento VARCHAR(300),
    criado_em           TIMESTAMPTZ    NOT NULL,
    cancelado_em        TIMESTAMPTZ,
    CONSTRAINT ck_venda_tipo CHECK (tipo IN ('AVULSA', 'RESERVA')),
    CONSTRAINT ck_venda_reserva CHECK ((tipo = 'RESERVA') = (reserva_id IS NOT NULL)),
    CONSTRAINT ck_venda_status CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
    CONSTRAINT ck_venda_pagamento_status CHECK (pagamento_status IN ('PENDENTE', 'RECEBIDO')),
    CONSTRAINT ck_venda_total CHECK (total >= 0),
    CONSTRAINT ck_venda_cancelamento CHECK ((status = 'CANCELADA') = (cancelado_em IS NOT NULL))
);

CREATE INDEX idx_venda_imovel_data ON venda (imovel_id, criado_em DESC);
CREATE INDEX idx_venda_data ON venda (criado_em DESC);

-- Nome, código e preço são copiados do produto no momento da venda: alterar o cadastro depois
-- não muda o que foi vendido.
CREATE TABLE item_venda (
    id                   UUID           PRIMARY KEY,
    venda_id             UUID           NOT NULL REFERENCES venda (id),
    produto_id           UUID           NOT NULL REFERENCES produto (id),
    produto_codigo       VARCHAR(40)    NOT NULL,
    produto_nome         VARCHAR(150)   NOT NULL,
    -- Se a venda baixou estoque deste item (o produto controlava estoque naquele momento).
    baixou_estoque       BOOLEAN        NOT NULL,
    quantidade           INTEGER        NOT NULL,
    quantidade_devolvida INTEGER        NOT NULL DEFAULT 0,
    preco_unitario       NUMERIC(12, 2) NOT NULL,
    subtotal             NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uk_item_venda_produto UNIQUE (venda_id, produto_id),
    CONSTRAINT ck_item_venda_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_item_venda_devolvida CHECK (quantidade_devolvida BETWEEN 0 AND quantidade),
    CONSTRAINT ck_item_venda_preco CHECK (preco_unitario >= 0),
    CONSTRAINT ck_item_venda_subtotal CHECK (subtotal = quantidade * preco_unitario)
);

CREATE INDEX idx_item_venda_produto ON item_venda (produto_id);

-- Chaves de idempotência: repetir uma requisição com a mesma chave devolve o resultado da
-- primeira, em vez de executar a operação de novo. A linha é gravada na mesma transação da
-- operação, então uma tentativa que falhou não deixa a chave ocupada.
CREATE TABLE chave_idempotencia (
    id                UUID         PRIMARY KEY,
    operacao          VARCHAR(40)  NOT NULL,
    chave             VARCHAR(100) NOT NULL,
    -- SHA-256 do conteúdo da requisição: a mesma chave com outro conteúdo é recusada.
    impressao_digital VARCHAR(64)  NOT NULL,
    recurso_id        UUID,
    criado_em         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_chave_idempotencia UNIQUE (operacao, chave)
);

ALTER TABLE venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_venda ENABLE ROW LEVEL SECURITY;
ALTER TABLE chave_idempotencia ENABLE ROW LEVEL SECURITY;
