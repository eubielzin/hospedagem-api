já # Etapa 1 + Cadastro de Imóveis — Design

Data: 2026-10-10

## Objetivo

Inicializar o backend do projeto de gestão de casas por temporada (com a infraestrutura de
deploy) e entregar o primeiro módulo funcional de ponta a ponta: cadastro de imóveis, com
persistência real em PostgreSQL, validações e testes. As demais etapas (hóspedes, reservas,
tarifas, financeiro, dashboard) ficam fora desta entrega e só começam após a validação desta.

## Decisões acordadas

| Tema | Decisão |
|---|---|
| Estrutura de pacotes | Por camada, igual ao `oxyquest-api/manager-service` (`presentation`, `application`, `domain`, `infrastructure`), e não por módulo como sugeria o prompt original |
| Autenticação | **Fora do escopo por enquanto.** Spring Security presente com `permitAll` e CSRF desligado, para que ligar autenticação depois seja só configuração |
| Banco | PostgreSQL no Supabase, inclusive em desenvolvimento. Não há Postgres local |
| Schema | Flyway + `ddl-auto=validate`. Nunca `update` |
| CORS | Origens explícitas via `CORS_ALLOWED_ORIGINS`. Nunca `*` |
| Segredos | Somente variáveis de ambiente, sem valor padrão no `application.yml` |
| DTOs | Records escritos à mão em `application/dto` (sem módulo `contracts` gerado de OpenAPI) |
| Migração V1 | Cria apenas as tabelas de imóvel. As demais tabelas do MVP entram em migrações próprias, na etapa que as implementa |
| Fotos | URL + ordem. Sem upload de arquivo (não há storage definido) |
| Frontend | **Fora deste repositório.** Chegou a ser criado e foi removido a pedido em 2026-10-10: aqui fica apenas a API |

Risco aceito: sem autenticação, a API publicada fica acessível a quem tiver a URL. Não inserir
dados reais de hóspedes antes da etapa de autenticação.

## Estrutura do repositório

```
backend/
  pom.xml, mvnw, .mvn/
  Dockerfile
  .env.example
  src/main/java/com/gestaocasas/
    GestaoCasasApplication.java
    presentation/controller/      ImovelController
    application/
      dto/                        records de request/response, ApiResponse, PageResponse
      mapper/                     ImovelMapper (MapStruct)
      usecase/imovel/             um use case por operação
    domain/
      entity/                     Imovel, ImovelFoto
      enums/                      StatusImovel
      exception/                  ImovelNotFoundException, FotoNotFoundException
      repository/                 ImovelRepository, ImovelFotoRepository
    infrastructure/config/        SecurityConfig, CorsConfig, OpenApiConfig, GlobalExceptionHandler
  src/main/resources/
    application.yml, application-local.yml, application-prod.yml
    db/migration/V1__create_imovel.sql
  src/test/java/com/gestaocasas/  unitários, MockMvc e integração
docker-compose.yml   sobe apenas o backend em container (opcional)
README.md
.gitignore
```

## Backend

### Convenções (herdadas da referência)

- Use cases são `@Component` com um método `execute(...)`, um por operação.
- Injeção por construtor. Controllers só delegam e montam a resposta.
- Entidades JPA com Lombok em `domain/entity`; repositórios Spring Data em `domain/repository`.
- MapStruct para entidade ↔ DTO. Entidades nunca saem pela API.
- Resposta sempre no envelope `{ "success": bool, "message": string, "data": ... }`.

### Modelo de dados (V1)

`imovel`

| Coluna | Tipo | Regra |
|---|---|---|
| id | UUID PK | gerado pela aplicação |
| nome | VARCHAR(120) | NOT NULL |
| descricao | TEXT | |
| logradouro, numero, complemento, bairro | VARCHAR | logradouro NOT NULL |
| cidade | VARCHAR(100) | NOT NULL |
| uf | VARCHAR(2) | NOT NULL, CHECK duas letras maiúsculas |
| cep | VARCHAR(9) | |
| capacidade_maxima | INTEGER | NOT NULL, CHECK > 0 |
| quartos, banheiros, camas | INTEGER | NOT NULL, CHECK >= 0 |
| valor_base_diaria | NUMERIC(12,2) | NOT NULL, CHECK >= 0 |
| horario_checkin, horario_checkout | TIME | NOT NULL |
| status | VARCHAR(20) | NOT NULL, CHECK IN (ATIVO, INATIVO, MANUTENCAO) |
| criado_em, atualizado_em | TIMESTAMPTZ | NOT NULL |

`imovel_comodidade` — `(imovel_id UUID FK, comodidade VARCHAR(80))`, PK composta.

`imovel_foto` — `id UUID PK`, `imovel_id UUID FK`, `url VARCHAR(1000) NOT NULL`,
`descricao VARCHAR(200)`, `ordem INTEGER NOT NULL`, `criado_em TIMESTAMPTZ`.
Índice em `imovel_id`.

Índices em `imovel(status)` e `imovel(lower(nome))`.

### Endpoints

| Método | Rota | Resposta |
|---|---|---|
| POST | `/imoveis` | 201 com o imóvel |
| GET | `/imoveis?status=&nome=&page=&size=&sort=` | 200 paginado |
| GET | `/imoveis/{id}` | 200 / 404 |
| PUT | `/imoveis/{id}` | 200 / 404 |
| PATCH | `/imoveis/{id}/inativar` | 200 / 404 |
| POST | `/imoveis/{id}/fotos` | 201 / 404 |
| DELETE | `/imoveis/{id}/fotos/{fotoId}` | 204 / 404 |
| GET | `/actuator/health` | 200 |

Não existe `DELETE /imoveis/{id}`: imóveis não são excluídos fisicamente.
`PUT` aceita o campo `status`, o que permite reativar ou marcar manutenção.

### Validação e erros

- Bean Validation nos records de request: obrigatórios, tamanhos, `@Positive`/`@PositiveOrZero`,
  UF com 2 letras, URL da foto com esquema http(s), `size` de página limitado a 100.
- `GlobalExceptionHandler`:
  - 400 — validação, com `errors: [{ field, message }]`; JSON malformado; parâmetro inválido
  - 404 — imóvel ou foto não encontrados
  - 409 — violação de integridade no banco
  - 500 — mensagem genérica, sem detalhe interno; detalhe só no log

### Configuração

- `application.yml`: `server.port=${PORT:8080}`, datasource a partir de `DATABASE_URL`,
  `DATABASE_USERNAME`, `DATABASE_PASSWORD`; Hikari com `maximum-pool-size` 5 e `minimum-idle` 1;
  `ddl-auto=validate`; `open-in-view=false`; Flyway ligado; Actuator expondo só `health`.
- `application-local.yml`: SQL no log. Carrega `backend/.env` via `spring.config.import`.
- `application-prod.yml`: sem SQL no log, Swagger UI desligável por variável.
- Variáveis: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `PORT`,
  `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`.
- Supabase: usar o **Session Pooler** (porta 5432, compatível com IPv4, prepared statements, JPA
  e Flyway) com `sslmode=require`. Não usar o Transaction Pooler (6543).

### Docker / Render

Dockerfile multi-stage: build com Maven + Temurin 21, runtime com Temurin 21 JRE, usuário não
root, `HEALTHCHECK` em `/actuator/health`, porta por `PORT`. `.dockerignore` exclui `.env` e
`target/`.

## Testes

| Tipo | Cobertura | Executável nesta máquina |
|---|---|---|
| Unitário (JUnit 5 + Mockito) | use cases de imóvel: criar, atualizar, inativar, buscar inexistente, fotos | Sim |
| Web (`@WebMvcTest`) | validações do controller, formato do envelope, 400/404 | Sim |
| Integração (Testcontainers PostgreSQL) | Flyway aplica V1, `validate` passa, CRUD real, CHECKs do banco, filtros e paginação | **Não** — exige Docker |

Os testes de integração ficam em classes `*IT` executadas pelo Failsafe (`mvn verify`), para que
`mvn test` funcione sem Docker. Não se usa H2.

## Fora do escopo

Autenticação e permissões, hóspedes, reservas, disponibilidade, tarifas por período, financeiro,
dashboard, auditoria, upload de arquivos de foto, as tabelas correspondentes, e todo o frontend.

## Pendência externa

Aplicar a V1 e subir a aplicação contra um banco real depende de o projeto Supabase existir e de
`backend/.env` estar preenchido.
