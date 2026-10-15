-- Canais de venda das reservas (recepção, OTAs). Nunca excluídos, só inativados.
CREATE TABLE canal (
    id            UUID         PRIMARY KEY,
    nome          VARCHAR(80)  NOT NULL,
    tipo          VARCHAR(10)  NOT NULL,
    ativo         BOOLEAN      NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_canal_tipo CHECK (tipo IN ('DIRETO', 'OTA', 'MOTOR'))
);

CREATE UNIQUE INDEX uk_canal_nome ON canal (lower(nome));

INSERT INTO canal (id, nome, tipo, ativo, criado_em, atualizado_em) VALUES
    (gen_random_uuid(), 'Direto (recepção)', 'DIRETO', true, now(), now()),
    (gen_random_uuid(), 'Booking.com', 'OTA', true, now(), now()),
    (gen_random_uuid(), 'Airbnb', 'OTA', true, now(), now()),
    (gen_random_uuid(), 'Expedia', 'OTA', true, now(), now());

-- Número do código exibido (CM-1001, CM-1002...). Pode pular números, nunca repete.
CREATE SEQUENCE reserva_codigo_seq START WITH 1001;

-- A UH (unidade habitacional) da reserva é o imóvel. O período é [checkin, checkout): a noite do
-- checkout não é ocupada, então uma reserva pode começar no dia em que outra termina.
CREATE TABLE reserva (
    id                    UUID           PRIMARY KEY,
    codigo                VARCHAR(20)    NOT NULL,
    imovel_id             UUID           NOT NULL REFERENCES imovel (id),
    hospede_id            UUID           NOT NULL REFERENCES hospede (id),
    canal_id              UUID           NOT NULL REFERENCES canal (id),
    checkin               DATE           NOT NULL,
    checkout              DATE           NOT NULL,
    adultos               INTEGER        NOT NULL,
    criancas              INTEGER        NOT NULL,
    valor_total           NUMERIC(12, 2) NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    observacoes           VARCHAR(1000),
    motivo_cancelamento   VARCHAR(500),
    checkin_realizado_em  TIMESTAMPTZ,
    checkout_realizado_em TIMESTAMPTZ,
    cancelada_em          TIMESTAMPTZ,
    criado_em             TIMESTAMPTZ    NOT NULL,
    atualizado_em         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_reserva_periodo CHECK (checkout > checkin),
    CONSTRAINT ck_reserva_adultos CHECK (adultos >= 1),
    CONSTRAINT ck_reserva_criancas CHECK (criancas >= 0),
    CONSTRAINT ck_reserva_valor CHECK (valor_total >= 0),
    CONSTRAINT ck_reserva_status CHECK (status IN
        ('RESERVADO', 'HOSPEDADO', 'CHECKOUT_REALIZADO', 'CANCELADA', 'NO_SHOW'))
);

CREATE UNIQUE INDEX uk_reserva_codigo ON reserva (codigo);
CREATE INDEX idx_reserva_imovel_periodo ON reserva (imovel_id, checkin, checkout);
CREATE INDEX idx_reserva_hospede ON reserva (hospede_id);
CREATE INDEX idx_reserva_checkin ON reserva (checkin);

-- Mesma barreira da V2: sem políticas, só o dono das tabelas (o backend) acessa.
ALTER TABLE canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE reserva ENABLE ROW LEVEL SECURITY;
