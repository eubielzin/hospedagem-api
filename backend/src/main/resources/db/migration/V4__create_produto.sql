-- Catálogo único de produtos da plataforma. O saldo não fica aqui: cada imóvel tem o seu estoque.
CREATE TABLE produto (
    id               UUID           PRIMARY KEY,
    codigo           VARCHAR(40)    NOT NULL,
    codigo_barras    VARCHAR(50),
    nome             VARCHAR(150)   NOT NULL,
    descricao        TEXT,
    categoria        VARCHAR(20)    NOT NULL,
    unidade          VARCHAR(10)    NOT NULL,
    preco_custo      NUMERIC(12, 2),
    preco_venda      NUMERIC(12, 2) NOT NULL,
    controla_estoque BOOLEAN        NOT NULL,
    ativo            BOOLEAN        NOT NULL,
    criado_em        TIMESTAMPTZ    NOT NULL,
    atualizado_em    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_produto_categoria CHECK (categoria IN ('CONSUMO', 'PASSEIO', 'TAXA', 'SERVICO')),
    CONSTRAINT ck_produto_preco_custo CHECK (preco_custo >= 0),
    CONSTRAINT ck_produto_preco_venda CHECK (preco_venda >= 0)
);

-- "coca01" e "COCA01" são o mesmo código.
CREATE UNIQUE INDEX uk_produto_codigo ON produto (lower(codigo));
CREATE UNIQUE INDEX uk_produto_codigo_barras ON produto (codigo_barras) WHERE codigo_barras IS NOT NULL;
CREATE INDEX idx_produto_categoria ON produto (categoria);
CREATE INDEX idx_produto_nome_lower ON produto (lower(nome));

-- Mesma barreira da V2: sem políticas, só o dono da tabela (o backend) acessa.
ALTER TABLE produto ENABLE ROW LEVEL SECURITY;
