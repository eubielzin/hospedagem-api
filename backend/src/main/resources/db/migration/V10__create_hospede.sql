-- Cadastro de hóspedes. Registros nunca são excluídos, só inativados (RN-15).
CREATE TABLE hospede (
    id                      UUID          PRIMARY KEY,
    nome                    VARCHAR(150)  NOT NULL,
    tipo_documento          VARCHAR(20)   NOT NULL,
    -- Pode ficar vazio no cadastro: o documento só é exigido no check-in (RN-04).
    numero_documento        VARCHAR(30)   NOT NULL,
    nacionalidade           VARCHAR(60)   NOT NULL,
    telefone                VARCHAR(30),
    ativo                   BOOLEAN       NOT NULL,
    criado_em               TIMESTAMPTZ   NOT NULL,
    atualizado_em           TIMESTAMPTZ   NOT NULL,
    -- Só letras e dígitos: "123.456.789-09" e "12345678909" são o mesmo documento.
    documento_busca         TEXT GENERATED ALWAYS AS (
                                upper(regexp_replace(numero_documento, '[^A-Za-z0-9]', '', 'g'))) STORED,
    nome_busca              TEXT GENERATED ALWAYS AS (normalizar_busca(nome)) STORED,
    CONSTRAINT ck_hospede_tipo_documento CHECK (tipo_documento IN ('CPF', 'RG', 'PASSAPORTE'))
);

-- Um mesmo documento não pode pertencer a dois hóspedes, ativos ou não.
CREATE UNIQUE INDEX uk_hospede_documento ON hospede (tipo_documento, documento_busca)
    WHERE documento_busca <> '';
CREATE INDEX idx_hospede_nome_busca ON hospede (nome_busca);

-- Mesma barreira da V2: sem políticas, só o dono da tabela (o backend) acessa.
ALTER TABLE hospede ENABLE ROW LEVEL SECURITY;
