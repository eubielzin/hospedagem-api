-- Saldo de cada produto em cada imóvel. O mesmo produto tem quantidades independentes por casa.
CREATE TABLE estoque_imovel (
    id               UUID        PRIMARY KEY,
    imovel_id        UUID        NOT NULL REFERENCES imovel (id),
    produto_id       UUID        NOT NULL REFERENCES produto (id),
    quantidade_atual INTEGER     NOT NULL DEFAULT 0,
    estoque_minimo   INTEGER     NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ NOT NULL,
    atualizado_em    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_estoque_imovel_produto UNIQUE (imovel_id, produto_id),
    -- Última barreira contra estoque negativo, mesmo com operações concorrentes.
    CONSTRAINT ck_estoque_imovel_quantidade CHECK (quantidade_atual >= 0),
    CONSTRAINT ck_estoque_imovel_minimo CHECK (estoque_minimo >= 0)
);

-- A constraint única já indexa (imovel_id, produto_id); este cobre a busca a partir do produto.
CREATE INDEX idx_estoque_imovel_produto ON estoque_imovel (produto_id);

ALTER TABLE estoque_imovel ENABLE ROW LEVEL SECURITY;
