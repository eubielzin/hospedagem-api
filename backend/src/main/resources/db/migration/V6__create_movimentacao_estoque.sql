-- Histórico de tudo o que alterou o saldo de um produto em um imóvel. Cada linha guarda o saldo
-- antes e depois, então o saldo atual de estoque_imovel pode ser conferido contra o histórico.
CREATE TABLE movimentacao_estoque (
    id              UUID         PRIMARY KEY,
    imovel_id       UUID         NOT NULL REFERENCES imovel (id),
    produto_id      UUID         NOT NULL REFERENCES produto (id),
    tipo            VARCHAR(30)  NOT NULL,
    quantidade      INTEGER      NOT NULL,
    saldo_anterior  INTEGER      NOT NULL,
    saldo_posterior INTEGER      NOT NULL,
    motivo          VARCHAR(300),
    -- Operação que originou a movimentação (uma transferência, uma venda), quando houver.
    referencia_tipo VARCHAR(30),
    referencia_id   UUID,
    criado_em       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_movimentacao_estoque_saldo FOREIGN KEY (imovel_id, produto_id)
        REFERENCES estoque_imovel (imovel_id, produto_id),
    CONSTRAINT ck_movimentacao_estoque_tipo CHECK (tipo IN (
        'ENTRADA', 'SAIDA_VENDA', 'PERDA', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'DEVOLUCAO',
        'TRANSFERENCIA_ENTRADA', 'TRANSFERENCIA_SAIDA')),
    CONSTRAINT ck_movimentacao_estoque_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_movimentacao_estoque_saldos CHECK (saldo_anterior >= 0 AND saldo_posterior >= 0),
    -- O saldo posterior tem de ser exatamente o anterior mais ou menos a quantidade, conforme o tipo.
    CONSTRAINT ck_movimentacao_estoque_aritmetica CHECK (
        (tipo IN ('ENTRADA', 'AJUSTE_POSITIVO', 'DEVOLUCAO', 'TRANSFERENCIA_ENTRADA')
            AND saldo_posterior = saldo_anterior + quantidade)
        OR (tipo IN ('SAIDA_VENDA', 'PERDA', 'AJUSTE_NEGATIVO', 'TRANSFERENCIA_SAIDA')
            AND saldo_posterior = saldo_anterior - quantidade)),
    CONSTRAINT ck_movimentacao_estoque_referencia CHECK ((referencia_tipo IS NULL) = (referencia_id IS NULL))
);

CREATE INDEX idx_movimentacao_estoque_imovel_data ON movimentacao_estoque (imovel_id, criado_em DESC);
CREATE INDEX idx_movimentacao_estoque_produto ON movimentacao_estoque (produto_id);
CREATE INDEX idx_movimentacao_estoque_referencia ON movimentacao_estoque (referencia_id)
    WHERE referencia_id IS NOT NULL;

-- O histórico é imutável: correções são feitas com uma nova movimentação, nunca alterando uma antiga.
CREATE FUNCTION impedir_alteracao_movimentacao_estoque() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'movimentacao_estoque é um histórico imutável: % não é permitido', TG_OP
        USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER trg_movimentacao_estoque_imutavel
    BEFORE UPDATE OR DELETE ON movimentacao_estoque
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_movimentacao_estoque();

CREATE TRIGGER trg_movimentacao_estoque_sem_truncate
    BEFORE TRUNCATE ON movimentacao_estoque
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_alteracao_movimentacao_estoque();

ALTER TABLE movimentacao_estoque ENABLE ROW LEVEL SECURITY;
