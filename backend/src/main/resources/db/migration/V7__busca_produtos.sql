-- Busca dinâmica de produtos: ignora maiúsculas e acentos e encontra o termo em qualquer parte do nome.

-- A extensão unaccent não serve para isto: unaccent() é STABLE, então não pode ser usada em coluna
-- gerada nem em índice. translate() é IMMUTABLE e nativo. As letras acentuadas maiúsculas estão
-- no mapa porque lower() só converte caracteres não ASCII em bancos com LC_CTYPE adequado.
CREATE FUNCTION normalizar_busca(texto TEXT) RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE STRICT PARALLEL SAFE
    SET search_path = ''
AS $$
    SELECT lower(translate(texto,
        'áàâãäåéèêëíìîïóòôõöúùûüçñýÿÁÀÂÃÄÅÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑÝŸ',
        'aaaaaaeeeeiiiiooooouuuucnyyaaaaaaeeeeiiiiooooouuuucnyy'))
$$;

-- Mantidas pelo banco: valem também para produtos gravados fora da aplicação.
ALTER TABLE produto
    ADD COLUMN nome_busca   TEXT GENERATED ALWAYS AS (normalizar_busca(nome)) STORED,
    ADD COLUMN codigo_busca TEXT GENERATED ALWAYS AS (normalizar_busca(codigo)) STORED;

-- Índices trigram: aceleram a busca por "contém" com 3 ou mais caracteres. Com 1 ou 2 caracteres
-- o PostgreSQL percorre a tabela de qualquer forma (medido: 50 ms em 50 mil produtos). São uma
-- otimização: se a extensão não puder ser criada, a migração segue e a busca funciona sem eles.
DO $$
DECLARE
    esquema TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        -- No Supabase as extensões ficam no schema "extensions", que já está no search_path.
        IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'extensions') THEN
            CREATE EXTENSION pg_trgm WITH SCHEMA extensions;
        ELSE
            CREATE EXTENSION pg_trgm;
        END IF;
    END IF;
    SELECT n.nspname INTO STRICT esquema
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'pg_trgm';

    EXECUTE format('CREATE INDEX idx_produto_nome_busca_trgm ON produto USING gin (nome_busca %I.gin_trgm_ops)',
                   esquema);
    EXECUTE format('CREATE INDEX idx_produto_codigo_busca_trgm ON produto USING gin (codigo_busca %I.gin_trgm_ops)',
                   esquema);
    EXECUTE format('CREATE INDEX idx_produto_codigo_barras_trgm ON produto '
                   'USING gin (lower(codigo_barras) %I.gin_trgm_ops)', esquema);
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'Índices trigram da busca de produtos não foram criados: %', SQLERRM;
END
$$;
