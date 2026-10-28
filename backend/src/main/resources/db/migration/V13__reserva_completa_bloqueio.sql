-- Reserva completa: regime de pensão, código na OTA, FNRH, encerramento (cancelamento e no-show com
-- multa), pendência autorizada no check-out, histórico de eventos e bloqueios de UH.

-- Hóspede: dados usados nas pessoas do dia (RF-53) e nos indicadores.
ALTER TABLE hospede
    ADD COLUMN data_nascimento         DATE,
    ADD COLUMN email                   VARCHAR(254),
    -- "Cidade/UF" ou "Cidade/País".
    ADD COLUMN endereco                VARCHAR(150),
    ADD COLUMN observacoes_alimentares VARCHAR(500);

-- O cancelamento passa a ser um caso do encerramento, que vale também para o no-show.
ALTER TABLE reserva RENAME COLUMN motivo_cancelamento TO encerramento_motivo;
ALTER TABLE reserva RENAME COLUMN cancelada_em TO encerrada_em;

ALTER TABLE reserva
    -- As refeições de cada regime ainda estão a validar com a pousada.
    ADD COLUMN regime_pensao               VARCHAR(20)   NOT NULL DEFAULT 'SEM_REFEICOES',
    ADD COLUMN codigo_externo              VARCHAR(60),
    -- FNRH (RN-04): preenchida inteira ou não preenchida.
    ADD COLUMN fnrh_cidade_procedencia     VARCHAR(120),
    ADD COLUMN fnrh_cidade_destino         VARCHAR(120),
    ADD COLUMN fnrh_motivo_viagem          VARCHAR(20),
    ADD COLUMN fnrh_meio_transporte        VARCHAR(20),
    ADD COLUMN fnrh_emitida_em             DATE,
    -- Dia do encerramento no fuso da pousada; os valores são digitados (política de multa a validar).
    ADD COLUMN encerramento_data           DATE,
    ADD COLUMN encerramento_multa          NUMERIC(12, 2),
    ADD COLUMN encerramento_valor_retido   NUMERIC(12, 2),
    ADD COLUMN encerramento_valor_devolver NUMERIC(12, 2),
    -- RN-05: check-out com saldo em aberto, autorizado.
    ADD COLUMN pendencia_autorizada        BOOLEAN       NOT NULL DEFAULT false;

UPDATE reserva
SET encerramento_data           = (encerrada_em AT TIME ZONE 'America/Sao_Paulo')::date,
    encerramento_multa          = 0,
    encerramento_valor_retido   = 0,
    encerramento_valor_devolver = 0
WHERE status IN ('CANCELADA', 'NO_SHOW');

ALTER TABLE reserva
    ALTER COLUMN regime_pensao DROP DEFAULT,
    ALTER COLUMN pendencia_autorizada DROP DEFAULT,
    ADD CONSTRAINT ck_reserva_regime_pensao CHECK (regime_pensao IN
        ('SEM_REFEICOES', 'CAFE_DA_MANHA', 'MEIA_PENSAO', 'PENSAO_COMPLETA')),
    ADD CONSTRAINT ck_reserva_fnrh CHECK (num_nulls(fnrh_cidade_procedencia, fnrh_cidade_destino,
        fnrh_motivo_viagem, fnrh_meio_transporte, fnrh_emitida_em) IN (0, 5)),
    ADD CONSTRAINT ck_reserva_fnrh_motivo CHECK (fnrh_motivo_viagem IN
        ('LAZER', 'NEGOCIOS', 'CONGRESSO', 'PARENTES', 'ESTUDOS', 'SAUDE', 'RELIGIAO', 'OUTRO')),
    ADD CONSTRAINT ck_reserva_fnrh_transporte CHECK (fnrh_meio_transporte IN
        ('AVIAO', 'CARRO', 'ONIBUS', 'MOTO', 'NAVIO', 'OUTRO')),
    ADD CONSTRAINT ck_reserva_encerramento CHECK ((status IN ('CANCELADA', 'NO_SHOW')) =
        (num_nulls(encerramento_data, encerramento_multa, encerramento_valor_retido,
                   encerramento_valor_devolver, encerrada_em) = 0)),
    ADD CONSTRAINT ck_reserva_encerramento_valores CHECK (encerramento_multa >= 0
        AND encerramento_valor_retido >= 0 AND encerramento_valor_devolver >= 0);

-- O mesmo código da OTA não entra duas vezes pelo mesmo canal.
CREATE UNIQUE INDEX uk_reserva_codigo_externo ON reserva (canal_id, codigo_externo)
    WHERE codigo_externo IS NOT NULL;
CREATE INDEX idx_reserva_checkout ON reserva (checkout);

-- Histórico da reserva (base da auditoria, RN-14): só recebe linhas novas.
CREATE TABLE reserva_evento (
    id         UUID         PRIMARY KEY,
    reserva_id UUID         NOT NULL REFERENCES reserva (id),
    data_hora  TIMESTAMPTZ  NOT NULL,
    descricao  VARCHAR(500) NOT NULL,
    -- Nulo quando a ação não veio de um usuário logado (dados migrados).
    usuario_id UUID REFERENCES usuario (id)
);

CREATE INDEX idx_reserva_evento_reserva ON reserva_evento (reserva_id, data_hora);

CREATE FUNCTION impedir_alteracao_reserva_evento() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    RAISE EXCEPTION 'reserva_evento é um histórico imutável: % não é permitido', TG_OP
        USING ERRCODE = 'restrict_violation';
END
$$;

CREATE TRIGGER trg_reserva_evento_imutavel
    BEFORE UPDATE OR DELETE ON reserva_evento
    FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_reserva_evento();

-- O que já aconteceu com as reservas existentes entra no histórico.
INSERT INTO reserva_evento (id, reserva_id, data_hora, descricao)
SELECT gen_random_uuid(), id, criado_em, 'Reserva criada' FROM reserva;
INSERT INTO reserva_evento (id, reserva_id, data_hora, descricao)
SELECT gen_random_uuid(), id, checkin_realizado_em, 'Check-in realizado' FROM reserva
WHERE checkin_realizado_em IS NOT NULL;
INSERT INTO reserva_evento (id, reserva_id, data_hora, descricao)
SELECT gen_random_uuid(), id, checkout_realizado_em, 'Check-out realizado' FROM reserva
WHERE checkout_realizado_em IS NOT NULL;
INSERT INTO reserva_evento (id, reserva_id, data_hora, descricao)
SELECT gen_random_uuid(), id, encerrada_em, 'Cancelada' || coalesce(': ' || encerramento_motivo, '') FROM reserva
WHERE status = 'CANCELADA';

-- Bloqueio de UH (RF-04): o fim é o último dia bloqueado (inclusive). Encerrado, nunca excluído (RN-15).
CREATE TABLE bloqueio (
    id           UUID         PRIMARY KEY,
    imovel_id    UUID         NOT NULL REFERENCES imovel (id),
    inicio       DATE         NOT NULL,
    fim          DATE         NOT NULL,
    motivo       VARCHAR(300) NOT NULL,
    usuario_id   UUID REFERENCES usuario (id),
    ativo        BOOLEAN      NOT NULL,
    criado_em    TIMESTAMPTZ  NOT NULL,
    encerrado_em TIMESTAMPTZ,
    CONSTRAINT ck_bloqueio_periodo CHECK (fim >= inicio),
    CONSTRAINT ck_bloqueio_encerramento CHECK (ativo = (encerrado_em IS NULL))
);

CREATE INDEX idx_bloqueio_imovel_periodo ON bloqueio (imovel_id, inicio, fim) WHERE ativo;

-- Mesma barreira da V2: sem políticas, só o dono das tabelas (o backend) acessa.
ALTER TABLE reserva_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE bloqueio ENABLE ROW LEVEL SECURITY;
