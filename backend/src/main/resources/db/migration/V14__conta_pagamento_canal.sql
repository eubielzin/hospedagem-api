-- Conta do hóspede (RF-38, RF-43), pagamentos de reserva e de venda do PDV (RF-44) e comissão e cor do canal.

-- Comissão (%, RF-19) e cor no mapa. As comissões reais das OTAs ainda estão a validar: começam em zero.
ALTER TABLE canal
    ADD COLUMN comissao NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cor      VARCHAR(7)    NOT NULL DEFAULT '#64748b';

UPDATE canal SET cor = CASE nome
    WHEN 'Direto (recepção)' THEN '#0f766e'
    WHEN 'Booking.com' THEN '#1d4ed8'
    WHEN 'Airbnb' THEN '#e11d48'
    WHEN 'Expedia' THEN '#ca8a04'
    ELSE cor END;

ALTER TABLE canal
    ALTER COLUMN comissao DROP DEFAULT,
    ALTER COLUMN cor DROP DEFAULT,
    ADD CONSTRAINT ck_canal_comissao CHECK (comissao BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_canal_cor CHECK (cor ~ '^#[0-9a-f]{6}$');

-- A comissão vale como estava quando a reserva foi feita: mudar o canal depois não muda reservas antigas.
ALTER TABLE reserva ADD COLUMN comissao_canal NUMERIC(5, 2) NOT NULL DEFAULT 0;
UPDATE reserva r SET comissao_canal = c.comissao FROM canal c WHERE c.id = r.canal_id;
ALTER TABLE reserva
    ALTER COLUMN comissao_canal DROP DEFAULT,
    ADD CONSTRAINT ck_reserva_comissao_canal CHECK (comissao_canal BETWEEN 0 AND 100);

-- Consumo lançado na conta do quarto. Nunca excluído: o estorno marca o lançamento (RN-14).
CREATE TABLE lancamento_conta (
    id             UUID           PRIMARY KEY,
    reserva_id     UUID           NOT NULL REFERENCES reserva (id),
    data           DATE           NOT NULL,
    descricao      VARCHAR(150)   NOT NULL,
    -- Com produto, o preço é o do cadastro e o estoque do imóvel da reserva é baixado.
    produto_id     UUID REFERENCES produto (id),
    baixou_estoque BOOLEAN        NOT NULL,
    quantidade     INTEGER        NOT NULL,
    valor_unitario NUMERIC(12, 2) NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    usuario_id     UUID REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ    NOT NULL,
    estornado      BOOLEAN        NOT NULL,
    motivo_estorno VARCHAR(300),
    estornado_em   TIMESTAMPTZ,
    estornado_por  UUID REFERENCES usuario (id),
    CONSTRAINT ck_lancamento_conta_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_lancamento_conta_valor CHECK (valor_unitario >= 0),
    CONSTRAINT ck_lancamento_conta_total CHECK (total = quantidade * valor_unitario),
    CONSTRAINT ck_lancamento_conta_estorno CHECK (estornado = (estornado_em IS NOT NULL)
        AND estornado = (motivo_estorno IS NOT NULL)),
    CONSTRAINT ck_lancamento_conta_estoque CHECK (NOT baixou_estoque OR produto_id IS NOT NULL)
);

CREATE INDEX idx_lancamento_conta_reserva ON lancamento_conta (reserva_id, criado_em);
CREATE INDEX idx_lancamento_conta_data ON lancamento_conta (data);

-- Recebimento: de uma reserva (conta do hóspede) ou de uma venda avulsa do PDV, nunca dos dois.
CREATE TABLE pagamento (
    id             UUID           PRIMARY KEY,
    reserva_id     UUID REFERENCES reserva (id),
    venda_id       UUID REFERENCES venda (id),
    data           DATE           NOT NULL,
    forma          VARCHAR(10)    NOT NULL,
    valor_bruto    NUMERIC(12, 2) NOT NULL,
    -- Taxa da maquininha ou do intermediador.
    taxa           NUMERIC(12, 2) NOT NULL,
    valor_liquido  NUMERIC(12, 2) NOT NULL,
    -- Só em dinheiro: quanto o cliente entregou, para o troco.
    valor_recebido NUMERIC(12, 2),
    descricao      VARCHAR(300)   NOT NULL,
    usuario_id     UUID REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_pagamento_origem CHECK (num_nonnulls(reserva_id, venda_id) = 1),
    CONSTRAINT ck_pagamento_forma CHECK (forma IN ('PIX', 'CREDITO', 'DEBITO', 'DINHEIRO', 'OUTRO')),
    CONSTRAINT ck_pagamento_valor CHECK (valor_bruto > 0),
    CONSTRAINT ck_pagamento_taxa CHECK (taxa >= 0 AND taxa <= valor_bruto),
    CONSTRAINT ck_pagamento_liquido CHECK (valor_liquido = valor_bruto - taxa),
    CONSTRAINT ck_pagamento_recebido CHECK (valor_recebido IS NULL OR (forma = 'DINHEIRO' AND valor_recebido >= valor_bruto))
);

CREATE INDEX idx_pagamento_reserva ON pagamento (reserva_id) WHERE reserva_id IS NOT NULL;
-- Uma venda é paga uma vez só.
CREATE UNIQUE INDEX uk_pagamento_venda ON pagamento (venda_id) WHERE venda_id IS NOT NULL;
CREATE INDEX idx_pagamento_data ON pagamento (data);

-- Mesma barreira da V2: sem políticas, só o dono das tabelas (o backend) acessa.
ALTER TABLE lancamento_conta ENABLE ROW LEVEL SECURITY;
ALTER TABLE pagamento ENABLE ROW LEVEL SECURITY;
