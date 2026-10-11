-- Fotos passam a poder ser arquivos no Supabase Storage. Os registros existentes continuam válidos:
-- são fotos cadastradas por URL (storage_provider = 'EXTERNA', sem storage_path).
ALTER TABLE imovel_foto
    ADD COLUMN storage_provider     VARCHAR(20) NOT NULL DEFAULT 'EXTERNA',
    ADD COLUMN storage_path         VARCHAR(500),
    ADD COLUMN principal            BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Preenchido quando a exclusão foi pedida mas o arquivo ainda não foi removido do Storage.
    ADD COLUMN exclusao_pendente_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_imovel_foto_storage_provider CHECK (storage_provider IN ('EXTERNA', 'SUPABASE')),
    ADD CONSTRAINT ck_imovel_foto_storage_path CHECK ((storage_provider = 'EXTERNA') = (storage_path IS NULL)),
    ADD CONSTRAINT ck_imovel_foto_principal_ativa CHECK (NOT (principal AND exclusao_pendente_em IS NOT NULL));

-- No máximo uma foto principal por imóvel, mesmo com requisições concorrentes.
CREATE UNIQUE INDEX uk_imovel_foto_principal ON imovel_foto (imovel_id) WHERE principal;
CREATE UNIQUE INDEX uk_imovel_foto_storage_path ON imovel_foto (storage_path) WHERE storage_path IS NOT NULL;
