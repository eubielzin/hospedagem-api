# Gestão de Casas por Temporada

API de gerenciamento de casas para locação por temporada. Este repositório contém apenas o
backend; o frontend não faz parte dele.

| Parte | Tecnologia | Hospedagem |
|---|---|---|
| `backend/` | Java 21, Spring Boot 4, JPA, Flyway | Render (Docker) |
| Banco | PostgreSQL | Supabase |

**Estado atual:** estrutura do projeto e módulo de **imóveis** (cadastro, edição, listagem com
filtros, inativação e fotos por URL). Ainda **não há autenticação**: qualquer pessoa com a URL
da API consegue ler e alterar os dados. Não cadastre dados reais de hóspedes antes de o módulo
de autenticação existir.

## Rodando localmente

Pré-requisitos: Java 21 e um projeto no Supabase. Não é preciso ter Maven instalado.

### 1. Banco (Supabase)

1. Crie um projeto em <https://supabase.com>.
2. Em **Project Settings → Database → Connection string**, escolha **Session pooler** (porta
   5432). Ele funciona em redes somente IPv4 (caso do Render) e é compatível com JPA e Flyway.
   Não use o Transaction pooler (porta 6543).
3. Monte a URL no formato JDBC:
   `jdbc:postgresql://HOST:5432/postgres?sslmode=require`

As tabelas são criadas pelo Flyway na primeira inicialização do backend. Não execute SQL
manualmente no painel do Supabase.

### 2. Backend

```bash
cd backend
cp .env.example .env      # preencha DATABASE_URL, DATABASE_USERNAME e DATABASE_PASSWORD
./mvnw spring-boot:run
```

- API: <http://localhost:8080>
- Documentação (Swagger): <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DATABASE_URL` | sim | URL JDBC do Session pooler, com `?sslmode=require` |
| `DATABASE_USERNAME` | sim | `postgres.<project-ref>` |
| `DATABASE_PASSWORD` | sim | Senha do banco |
| `CORS_ALLOWED_ORIGINS` | em produção | Origens (sites) autorizadas a chamar a API pelo navegador, separadas por vírgula. Padrão: `http://localhost:5173` |
| `PORT` | não | Porta HTTP. Padrão `8080`; o Render define automaticamente |
| `SPRING_PROFILES_ACTIVE` | em produção | `prod` no Render. Padrão: `local` |
| `SWAGGER_ENABLED` | não | `true` para expor o Swagger no perfil `prod`. Padrão: `false` |

## Testes

```bash
cd backend
./mvnw test      # unitários e de controller — não precisam de banco nem de Docker
./mvnw verify    # inclui os testes de integração (*IT) com Testcontainers — exige Docker
```

Os testes de integração sobem um PostgreSQL real em container; H2 não é usado.

## Estrutura do backend

```
com.gestaocasas
├── presentation/controller    recebe a requisição e devolve a resposta
├── application
│   ├── usecase/<módulo>       uma classe por operação, com as regras de negócio
│   ├── mapper                 conversão entidade ↔ DTO (MapStruct)
│   └── dto                    contratos de entrada e saída da API
├── domain
│   ├── entity                 entidades JPA
│   ├── enums / exception
│   └── repository             contratos de persistência (Spring Data)
└── infrastructure/config      segurança, CORS, OpenAPI, tratamento global de erros
```

Toda resposta segue o envelope `{ "success", "message", "data" }`; erros de validação trazem
`errors: [{ "field", "message" }]`.

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/imoveis` | Cria um imóvel |
| GET | `/imoveis?status=&nome=&page=&size=&sort=` | Lista com filtros e paginação |
| GET | `/imoveis/{id}` | Consulta um imóvel |
| PUT | `/imoveis/{id}` | Atualiza (inclusive o status) |
| PATCH | `/imoveis/{id}/inativar` | Inativa |
| POST | `/imoveis/{id}/fotos` | Cadastra uma foto (URL) |
| DELETE | `/imoveis/{id}/fotos/{fotoId}` | Remove uma foto |
| GET | `/actuator/health` | Health check |

Imóveis não são excluídos fisicamente: não existe `DELETE /imoveis/{id}`.

### Migrações

Ficam em `backend/src/main/resources/db/migration`. Uma migração já aplicada nunca é alterada:
mudanças de schema entram em um novo arquivo `V<n>__descricao.sql`. O Hibernate roda com
`ddl-auto=validate` e apenas confere se as entidades batem com o banco.

## Deploy no Render

1. Envie o repositório para o GitHub.
2. No Render: **New → Web Service** e conecte o repositório.
3. Configure:
   - **Root Directory:** `backend`
   - **Runtime:** Docker (o `Dockerfile` é detectado automaticamente)
   - **Health Check Path:** `/actuator/health`
4. Em **Environment**, defina `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`,
   `SPRING_PROFILES_ACTIVE=prod` e `CORS_ALLOWED_ORIGINS` com a URL do site que vai consumir a
   API (sem barra no final). Não defina `PORT`.
5. Faça o deploy e confira `https://<servico>.onrender.com/actuator/health`.

No plano gratuito o serviço hiberna após um período sem requisições, e a primeira chamada
seguinte pode levar perto de um minuto.
