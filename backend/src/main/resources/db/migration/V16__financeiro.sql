-- Financeiro (RF-44 a RF-50): despesas e conciliação do repasse das OTAs.

-- Despesa a pagar ou paga. Categoria em texto livre por enquanto.
CREATE TABLE despesa (
    id             UUID           PRIMARY KEY,
    categoria      VARCHAR(80)    NOT NULL,
    fornecedor     VARCHAR(120)   NOT NULL,
    descricao      VARCHAR(300),
    valor          NUMERIC(12, 2) NOT NULL,
    vencimento     DATE           NOT NULL,
    data_pagamento DATE,
    -- Referência do comprovante (número, link ou observação).
    comprovante    VARCHAR(300),
    usuario_id     UUID REFERENCES usuario (id),
    criado_em      TIMESTAMPTZ    NOT NULL,
    atualizado_em  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_despesa_valor CHECK (valor > 0),
    CONSTRAINT ck_despesa_comprovante CHECK (comprovante IS NULL OR data_pagamento IS NOT NULL)
);

CREATE INDEX idx_despesa_vencimento ON despesa (vencimento);
CREATE INDEX idx_despesa_pagamento ON despesa (data_pagamento) WHERE data_pagamento IS NOT NULL;

-- RF-46: dia em que o repasse da OTA (diárias menos comissão) caiu na conta da pousada.
ALTER TABLE reserva ADD COLUMN repasse_recebido_em DATE;

ALTER TABLE despesa ENABLE ROW LEVEL SECURITY;
