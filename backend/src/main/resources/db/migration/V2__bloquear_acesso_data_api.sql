-- O Supabase expõe o schema public pela Data API (PostgREST) aos papéis anon e authenticated.
-- Esta aplicação acessa o banco somente pelo backend, então esses papéis não devem ter acesso.
-- Os papéis só existem no Supabase; em um PostgreSQL comum (testes) o bloco não faz nada.
DO $$
DECLARE
    papel TEXT;
BEGIN
    FOREACH papel IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = papel) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', papel);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', papel);
            -- Vale também para as tabelas criadas pelas próximas migrações.
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', papel);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', papel);
        END IF;
    END LOOP;
END
$$;

-- Segunda barreira: com RLS ligado e sem políticas, apenas o dono das tabelas (o backend) acessa.
ALTER TABLE imovel ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_comodidade ENABLE ROW LEVEL SECURITY;
ALTER TABLE imovel_foto ENABLE ROW LEVEL SECURITY;
