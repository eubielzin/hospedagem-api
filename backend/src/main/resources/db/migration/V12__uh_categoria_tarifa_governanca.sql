-- O imóvel passa a ser a UH (unidade habitacional) do levantamento: ganha tipo, categoria,
-- capacidade separada de adultos e crianças e o status de governança.

-- Configurações da pousada (linha única). Os horários daqui são o padrão dos imóveis que não
-- tiverem horário próprio.
CREATE TABLE configuracao (
    id               SMALLINT     PRIMARY KEY,
    nome_pousada     VARCHAR(120) NOT NULL,
    cidade           VARCHAR(100) NOT NULL,
    horario_checkin  TIME         NOT NULL,
    horario_checkout TIME         NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_configuracao_unica CHECK (id = 1)
);

INSERT INTO configuracao (id, nome_pousada, cidade, horario_checkin, horario_checkout, atualizado_em)
VALUES (1, 'Pousada Casas Marias', 'Barreirinhas/MA', '14:00', '12:00', now());

-- Categoria da UH (RF-30). A tarifa é definida por categoria e período (RF-31).
CREATE TABLE categoria_uh (
    id            UUID         PRIMARY KEY,
    nome          VARCHAR(80)  NOT NULL,
    nivel         INTEGER      NOT NULL,
    descricao     VARCHAR(500),
    ativa         BOOLEAN      NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_categoria_uh_nivel CHECK (nivel >= 1)
);

CREATE UNIQUE INDEX uk_categoria_uh_nome ON categoria_uh (lower(nome));

-- Tarifas podem se sobrepor: em cada noite vale a de período mais curto (a mais específica).
CREATE TABLE tarifa (
    id              UUID           PRIMARY KEY,
    categoria_id    UUID           NOT NULL REFERENCES categoria_uh (id),
    nome            VARCHAR(80)    NOT NULL,
    inicio          DATE           NOT NULL,
    -- Última noite (inclusive) em que a tarifa vale.
    fim             DATE           NOT NULL,
    valor_diaria    NUMERIC(12, 2) NOT NULL,
    estadia_minima  INTEGER        NOT NULL,
    fechado_chegada BOOLEAN        NOT NULL,
    fechado_saida   BOOLEAN        NOT NULL,
    venda_fechada   BOOLEAN        NOT NULL,
    criado_em       TIMESTAMPTZ    NOT NULL,
    atualizado_em   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_tarifa_periodo CHECK (fim >= inicio),
    CONSTRAINT ck_tarifa_valor CHECK (valor_diaria > 0),
    CONSTRAINT ck_tarifa_estadia_minima CHECK (estadia_minima >= 1)
);

CREATE INDEX idx_tarifa_categoria_periodo ON tarifa (categoria_id, inicio, fim);

ALTER TABLE imovel
    ADD COLUMN tipo                VARCHAR(20) NOT NULL DEFAULT 'CASA',
    ADD COLUMN categoria_id        UUID REFERENCES categoria_uh (id),
    ADD COLUMN capacidade_adultos  INTEGER,
    ADD COLUMN capacidade_criancas INTEGER,
    -- Só o que a governança define à mão. OCUPADA (reserva hospedada) e BLOQUEADA (bloqueio
    -- vigente) são calculadas na leitura, nunca gravadas.
    ADD COLUMN status_governanca   VARCHAR(20) NOT NULL DEFAULT 'LIVRE';

-- A capacidade antiga era um total: vira toda de adultos, e a de crianças é ajustada no cadastro.
UPDATE imovel SET capacidade_adultos = capacidade_maxima, capacidade_criancas = 0;

-- O status MANUTENCAO do imóvel era só informativo; a manutenção passa a ser da governança.
UPDATE imovel SET status = 'ATIVO', status_governanca = 'MANUTENCAO' WHERE status = 'MANUTENCAO';

ALTER TABLE imovel
    ALTER COLUMN tipo DROP DEFAULT,
    ALTER COLUMN status_governanca DROP DEFAULT,
    ALTER COLUMN capacidade_adultos SET NOT NULL,
    ALTER COLUMN capacidade_criancas SET NOT NULL,
    -- Sem horário próprio, vale o da configuração.
    ALTER COLUMN horario_checkin DROP NOT NULL,
    ALTER COLUMN horario_checkout DROP NOT NULL,
    DROP CONSTRAINT ck_imovel_capacidade,
    -- A capacidade máxima passa a ser calculada: adultos + crianças.
    DROP COLUMN capacidade_maxima,
    DROP CONSTRAINT ck_imovel_status,
    ADD CONSTRAINT ck_imovel_status CHECK (status IN ('ATIVO', 'INATIVO')),
    ADD CONSTRAINT ck_imovel_tipo CHECK (tipo IN ('CASA', 'IMOVEL_COMPLETO')),
    ADD CONSTRAINT ck_imovel_capacidade_adultos CHECK (capacidade_adultos >= 1),
    ADD CONSTRAINT ck_imovel_capacidade_criancas CHECK (capacidade_criancas >= 0),
    ADD CONSTRAINT ck_imovel_status_governanca CHECK (status_governanca IN ('LIVRE', 'SUJA', 'MANUTENCAO'));

-- Só existe um imóvel completo: ele reúne todas as casas (RN-02).
CREATE UNIQUE INDEX uk_imovel_completo ON imovel (tipo) WHERE tipo = 'IMOVEL_COMPLETO';
CREATE INDEX idx_imovel_categoria ON imovel (categoria_id);

-- Mesma barreira da V2: sem políticas, só o dono das tabelas (o backend) acessa.
ALTER TABLE configuracao ENABLE ROW LEVEL SECURITY;
ALTER TABLE categoria_uh ENABLE ROW LEVEL SECURITY;
ALTER TABLE tarifa ENABLE ROW LEVEL SECURITY;
