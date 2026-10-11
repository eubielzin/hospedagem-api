CREATE TABLE imovel (
    id                UUID           PRIMARY KEY,
    nome              VARCHAR(120)   NOT NULL,
    descricao         TEXT,
    logradouro        VARCHAR(200)   NOT NULL,
    numero            VARCHAR(20),
    complemento       VARCHAR(100),
    bairro            VARCHAR(100),
    cidade            VARCHAR(100)   NOT NULL,
    uf                VARCHAR(2)     NOT NULL,
    cep               VARCHAR(9),
    capacidade_maxima INTEGER        NOT NULL,
    quartos           INTEGER        NOT NULL,
    banheiros         INTEGER        NOT NULL,
    camas             INTEGER        NOT NULL,
    valor_base_diaria NUMERIC(12, 2) NOT NULL,
    horario_checkin   TIME           NOT NULL,
    horario_checkout  TIME           NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    criado_em         TIMESTAMPTZ    NOT NULL,
    atualizado_em     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_imovel_uf CHECK (uf ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_imovel_capacidade CHECK (capacidade_maxima > 0),
    CONSTRAINT ck_imovel_quartos CHECK (quartos >= 0),
    CONSTRAINT ck_imovel_banheiros CHECK (banheiros >= 0),
    CONSTRAINT ck_imovel_camas CHECK (camas >= 0),
    CONSTRAINT ck_imovel_valor_base CHECK (valor_base_diaria >= 0),
    CONSTRAINT ck_imovel_status CHECK (status IN ('ATIVO', 'INATIVO', 'MANUTENCAO'))
);

CREATE INDEX idx_imovel_status ON imovel (status);
CREATE INDEX idx_imovel_nome_lower ON imovel (lower(nome));

CREATE TABLE imovel_comodidade (
    imovel_id  UUID        NOT NULL REFERENCES imovel (id),
    comodidade VARCHAR(80) NOT NULL,
    PRIMARY KEY (imovel_id, comodidade)
);

CREATE TABLE imovel_foto (
    id        UUID          PRIMARY KEY,
    imovel_id UUID          NOT NULL REFERENCES imovel (id),
    url       VARCHAR(1000) NOT NULL,
    descricao VARCHAR(200),
    ordem     INTEGER       NOT NULL,
    criado_em TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_imovel_foto_ordem CHECK (ordem >= 0)
);

CREATE INDEX idx_imovel_foto_imovel ON imovel_foto (imovel_id);
