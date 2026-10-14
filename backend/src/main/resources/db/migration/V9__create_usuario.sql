-- Usuários do painel administrativo. A senha é guardada somente como hash BCrypt.
CREATE TABLE usuario (
    id            UUID         PRIMARY KEY,
    nome          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    ativo         BOOLEAN      NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL
);

-- "Ana@Exemplo.com" e "ana@exemplo.com" são o mesmo e-mail.
CREATE UNIQUE INDEX uk_usuario_email ON usuario (lower(email));

-- Mesma barreira da V2: sem políticas, só o dono da tabela (o backend) acessa.
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
