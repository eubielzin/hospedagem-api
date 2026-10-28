-- Configuração da pousada (RF-67): idade limite de criança, política de cancelamento e formas de pagamento aceitas.
-- Os valores iniciais são os do protótipo e ainda estão a validar com a pousada.
ALTER TABLE configuracao
    ADD COLUMN idade_limite_crianca  INTEGER       NOT NULL DEFAULT 12,
    ADD COLUMN politica_cancelamento VARCHAR(2000) NOT NULL DEFAULT '[a validar com o cliente]',
    -- Lista separada por vírgulas, na ordem de exibição.
    ADD COLUMN formas_pagamento      VARCHAR(60)   NOT NULL DEFAULT 'PIX,CREDITO,DEBITO,DINHEIRO,OUTRO';

ALTER TABLE configuracao
    ALTER COLUMN idade_limite_crianca DROP DEFAULT,
    ALTER COLUMN politica_cancelamento DROP DEFAULT,
    ALTER COLUMN formas_pagamento DROP DEFAULT,
    ADD CONSTRAINT ck_configuracao_idade CHECK (idade_limite_crianca BETWEEN 0 AND 17),
    ADD CONSTRAINT ck_configuracao_formas CHECK (formas_pagamento ~ '^(PIX|CREDITO|DEBITO|DINHEIRO|OUTRO)(,(PIX|CREDITO|DEBITO|DINHEIRO|OUTRO))*$');
