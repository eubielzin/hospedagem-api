# Gestão de Casas por Temporada

API de gerenciamento de casas para locação por temporada. Este repositório contém apenas o
backend; o frontend não faz parte dele.

| Parte | Tecnologia | Hospedagem |
|---|---|---|
| `backend/` | Java 21, Spring Boot 4, JPA, Flyway | Render (Docker) |
| Banco | PostgreSQL | Supabase |

**Estado atual:** estrutura do projeto e módulo de **imóveis** (cadastro, edição, listagem com
filtros, inativação e fotos, por URL ou por upload para o Supabase Storage). Ainda **não há
autenticação**: qualquer pessoa com a URL da API consegue ler e alterar os dados. Não cadastre
dados reais de hóspedes antes de o módulo de autenticação existir. Por esse motivo o upload, a
exclusão e a troca da foto principal ficam **desabilitados por padrão** (veja
[Fotos dos imóveis](#fotos-dos-imóveis)).

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
| `SUPABASE_URL` | para upload | `https://<project-ref>.supabase.co` |
| `SUPABASE_STORAGE_BUCKET` | não | Bucket das imagens. Padrão: `imoveis` |
| `SUPABASE_SECRET_KEY` | para upload | Secret key do Supabase (Project Settings → API Keys). Só no backend; nunca a publishable key |
| `FOTOS_ESCRITA_HABILITADA` | não | `true` libera upload, exclusão e foto principal. Padrão: `false`. Use somente em ambiente local |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | não | Limite por arquivo. Padrão: `5MB` |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | não | Limite da requisição de upload. Padrão: `6MB` |

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
│   ├── validation             validação do conteúdo das imagens
│   └── dto                    contratos de entrada e saída da API
├── domain
│   ├── entity                 entidades JPA
│   ├── enums / exception
│   └── repository             contratos de persistência (Spring Data)
│   └── storage                contrato de armazenamento de arquivos (StoragePort)
└── infrastructure
    ├── config                 segurança, CORS, OpenAPI, tratamento global de erros
    └── storage                cliente HTTP do Supabase Storage
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
| GET | `/imoveis/{id}/fotos` | Lista as fotos, na ordem de exibição |
| POST | `/imoveis/{id}/fotos` | Cadastra uma foto por URL externa |
| POST | `/imoveis/{id}/fotos/upload` | Envia uma imagem para o Storage 🔒 |
| PATCH | `/imoveis/{id}/fotos/{fotoId}/principal` | Define a foto principal 🔒 |
| DELETE | `/imoveis/{id}/fotos/{fotoId}` | Exclui uma foto 🔒 |
| GET | `/actuator/health` | Health check |

🔒 Respondem `403` enquanto `FOTOS_ESCRITA_HABILITADA` não for `true`.

Imóveis não são excluídos fisicamente: não existe `DELETE /imoveis/{id}`.

## Fotos dos imóveis

As imagens ficam no bucket `imoveis` do Supabase Storage, que **já existe** e não precisa ser
recriado (leitura pública, 5 MB, `image/jpeg`, `image/png`, `image/webp`, sem policy pública de
escrita). O banco guarda apenas a referência, na tabela `imovel_foto`.

### Organização no bucket

```
imoveis/
└── <id do imóvel>/
    ├── <uuid>.jpg
    └── <uuid>.webp
```

O caminho é gerado pelo backend. O nome original do arquivo não é usado e o cliente não escolhe
caminho nem extensão: a extensão vem do formato detectado no conteúdo.

### Validação

São aceitos JPEG, PNG e WebP de até 5 MB e 8000 px por lado (40 megapixels). O backend confere a
assinatura do arquivo e exige que ela bata com o `Content-Type` informado; SVG, HTML e qualquer
outro conteúdo são recusados.

| Situação | Status |
|---|---|
| Arquivo vazio, corrompido, conteúdo diferente do tipo informado, dimensões acima do limite | 400 |
| Arquivo acima de 5 MB | 413 |
| Tipo não permitido | 415 |
| Imóvel ou foto inexistente, ou foto de outro imóvel | 404 |
| Falha no Supabase Storage | 502 |
| Escrita desabilitada no ambiente | 403 |

### Exemplos

```bash
# Upload (campos opcionais: descricao, principal)
curl -X POST "http://localhost:8080/imoveis/ID_DO_IMOVEL/fotos/upload" \
  -F "file=@fachada.jpg;type=image/jpeg" \
  -F "descricao=Fachada" \
  -F "principal=true"
```

```json
{
  "success": true,
  "message": "Imagem enviada com sucesso",
  "data": {
    "id": "0b6f0a52-6a53-4f0e-9a3c-0d7b1f1f6c11",
    "url": "https://<project-ref>.supabase.co/storage/v1/object/public/imoveis/<id do imóvel>/<uuid>.jpg",
    "descricao": "Fachada",
    "ordem": 0,
    "principal": true
  }
}
```

```bash
curl "http://localhost:8080/imoveis/ID_DO_IMOVEL/fotos"                                  # listar
curl -X PATCH "http://localhost:8080/imoveis/ID_DO_IMOVEL/fotos/ID_DA_FOTO/principal"   # principal
curl -X DELETE "http://localhost:8080/imoveis/ID_DO_IMOVEL/fotos/ID_DA_FOTO"            # excluir (204)
```

Erro:

```json
{ "success": false, "message": "O conteúdo do arquivo não corresponde ao tipo informado" }
```

### Foto principal e ordem

Cada imóvel tem no máximo uma foto principal (garantido também por um índice único no banco). A
primeira foto enviada por upload vira a principal; depois disso, só muda com `principal=true` no
upload ou pelo `PATCH`. Ao excluir a principal, a próxima foto na ordem assume. Cada upload entra
no fim da ordem.

### Consistência entre banco e Storage

Banco e Storage não compartilham transação, então:

- **Upload:** o arquivo é enviado e depois a referência é gravada. Se a gravação falhar, o
  backend remove o arquivo recém-enviado. Se essa remoção também falhar, o log registra
  `OBJETO ORFAO NO STORAGE` com o caminho, para limpeza manual.
- **Exclusão:** a foto é marcada como exclusão pendente (deixa de aparecer nas consultas), o
  arquivo é removido do Storage e só então o registro é apagado. Se o Storage falhar, a resposta
  é `502` e o registro permanece com o caminho do arquivo: **repita o `DELETE`** para concluir.
  Pendências podem ser consultadas com
  `SELECT id, storage_path FROM imovel_foto WHERE exclusao_pendente_em IS NOT NULL`.

Fotos cadastradas por URL continuam funcionando; nelas não há arquivo para remover.

### Limitação de segurança

Upload, exclusão e foto principal usam a secret key do Supabase, e a API ainda não identifica
quem está chamando. Por isso essas três rotas respondem `403` a menos que
`FOTOS_ESCRITA_HABILITADA=true`. Habilite somente em ambiente local. **Não habilite no Render
enquanto não houver autenticação:** qualquer pessoa com a URL poderia enviar e apagar arquivos
do bucket. CORS não protege contra isso, porque só restringe navegadores.

As demais rotas de escrita (imóveis e foto por URL) continuam abertas, como já eram.

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
   API (sem barra no final). Não defina `PORT`. Para as fotos, defina `SUPABASE_URL` e
   `SUPABASE_SECRET_KEY`, e **não** defina `FOTOS_ESCRITA_HABILITADA` (veja
   [Limitação de segurança](#limitação-de-segurança)).
5. Faça o deploy e confira `https://<servico>.onrender.com/actuator/health`.

No plano gratuito o serviço hiberna após um período sem requisições, e a primeira chamada
seguinte pode levar perto de um minuto.
