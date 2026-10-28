# Gestão de Casas por Temporada

API de gerenciamento de casas para locação por temporada. Este repositório contém apenas o
backend; o frontend não faz parte dele.

| Parte | Tecnologia | Hospedagem |
|---|---|---|
| `backend/` | Java 21, Spring Boot 4, JPA, Flyway | Render (Docker) |
| Banco | PostgreSQL | Supabase |

**Estado atual:** estrutura do projeto e módulo de **imóveis** (cadastro, edição, listagem com
filtros, inativação e fotos, por URL ou por upload para o Supabase Storage), com
**autenticação** por e-mail e senha e token JWT (veja [Autenticação](#autenticação)). Só a
leitura de imóveis e fotos é pública; todo o resto exige login.

Também existem **produtos, estoque por casa e PDV**: cadastro de produtos, saldo por imóvel
(consulta, alertas e estoque mínimo), movimentações (entradas, ajustes, perdas e transferências
entre casas), busca dinâmica de produtos e vendas avulsas com cancelamento, devolução e pagamento.
A operação da pousada cobre reservas (cotação por tarifa, bloqueios, FNRH, remanejamento, no-show),
conta do hóspede, painel e pessoas do dia, financeiro e indicadores.

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
cp .env.example .env     
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
| `CORS_ALLOWED_ORIGINS` | em produção | Origens (sites) autorizadas a chamar a API pelo navegador, separadas por vírgula. Padrão: `http://localhost:3000,http://localhost:5173` (Next.js e Vite em desenvolvimento) |
| `PORT` | não | Porta HTTP. Padrão `8080`; o Render define automaticamente |
| `SPRING_PROFILES_ACTIVE` | em produção | `prod` no Render. Padrão: `local` |
| `SWAGGER_ENABLED` | não | `true` para expor o Swagger no perfil `prod`. Padrão: `false` |
| `SUPABASE_URL` | para upload | `https://<project-ref>.supabase.co` |
| `SUPABASE_STORAGE_BUCKET` | não | Bucket das imagens. Padrão: `imoveis` |
| `SUPABASE_SECRET_KEY` | para upload | Secret key do Supabase (Project Settings → API Keys). Só no backend; nunca a publishable key |
| `JWT_SECRET` | sim | Segredo que assina os tokens, com 32 caracteres ou mais. Gere com `openssl rand -base64 48`. Sem ele a aplicação não sobe. Trocar o valor invalida todos os tokens emitidos |
| `JWT_VALIDADE` | não | Validade do token. Padrão: `8h` (aceita `30m`, `12h`, `1d`...) |
| `ADMIN_EMAIL` | na primeira subida | E-mail do primeiro usuário, criado só se a tabela `usuario` estiver vazia |
| `ADMIN_SENHA` | na primeira subida | Senha do primeiro usuário (8 a 72 caracteres). Ignorada depois que existir algum usuário |
| `ADMIN_NOME` | não | Nome do primeiro usuário. Padrão: `Administrador` |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | não | Limite por arquivo. Padrão: `5MB` |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | não | Limite da requisição de upload. Padrão: `6MB` |
| `PRODUTOS_BUSCA_LIMITE_MAXIMO` | não | Teto de resultados da busca dinâmica de produtos. Padrão: `50` |
| `DEMO_SEED` | não | `true` cria dados sintéticos de demonstração (RN-16: categorias, 6 casas, imóvel completo, tarifas, comissões das OTAs, hóspedes, cerca de 130 reservas de 60 dias atrás a 45 à frente, consumos, pagamentos e despesas) na subida, só se não houver nenhum imóvel. Nunca em produção. Padrão: `false` |

## Testes

```bash
cd backend
./mvnw test 
./mvnw verify 
```

Os testes de integração sobem a aplicação inteira e rodam todas as migrações em um PostgreSQL
de verdade; H2 não é usado. Cada classe `*IT` recebe um banco novo e vazio. O servidor vem de
um destes dois lugares:

- **Docker (padrão):** um container `postgres:17-alpine` do Testcontainers, iniciado uma vez e
  usado por todas as classes.
- **PostgreSQL já em execução**, para máquinas sem Docker: defina `IT_DATABASE_URL` (por
  exemplo `jdbc:postgresql://localhost:5432/postgres`), `IT_DATABASE_USERNAME` e
  `IT_DATABASE_PASSWORD`. O usuário precisa poder criar bancos; os testes criam bancos `it_*` e
  não os apagam. **Nunca aponte para o Supabase.**

| O que é testado | Onde |
|---|---|
| Cadastro de produtos, unicidade de código e de código de barras | `ProdutoPersistenceIT`, `CreateProdutoUseCaseTest`, `ProdutoControllerTest` |
| Busca parcial, sem acentos, sem diferenciar maiúsculas, por código e código de barras | `ProdutoBuscaIT`, `BuscarProdutosUseCaseTest` |
| Saldo independente por casa, estoque mínimo e alertas | `EstoqueImovelPersistenceIT`, `ListarEstoqueUseCasesTest` |
| Entradas, ajustes, perdas e estoque insuficiente | `MovimentacaoEstoquePersistenceIT`, `MovimentadorEstoqueTest` |
| Transferências entre casas, inclusive cruzadas e simultâneas | `MovimentacaoEstoquePersistenceIT`, `TransferirEstoqueUseCaseTest` |
| Histórico imutável e constraints do banco | `MovimentacaoEstoquePersistenceIT`, `EstoqueImovelPersistenceIT` |
| Venda com baixa de estoque, atomicidade e preço histórico | `VendaPdvIT`, `RegistrarVendaUseCaseTest` |
| Vendas simultâneas sem vender além do saldo | `VendaPdvIT` |
| Cancelamento e devolução | `VendaPdvIT`, `CancelarEDevolverVendaUseCasesTest` |
| Idempotência, inclusive com requisições simultâneas | `VendaPdvIT`, `IdempotenciaTest` |
| Bloqueio das rotas administrativas em produção | `ProdutoAcessoBloqueadoTest`, `EstoqueAcessoBloqueadoTest` |

## Estrutura do backend

```
com.gestaocasas
├── presentation/controller    recebe a requisição e devolve a resposta
├── application
│   ├── usecase/<módulo>       uma classe por operação, com as regras de negócio
│   ├── service                regras compartilhadas por casos de uso (movimentação de estoque, idempotência)
│   ├── mapper                 conversão entidade ↔ DTO (MapStruct)
│   ├── validation             validação do conteúdo das imagens e unicidade de produtos
│   └── dto                    contratos de entrada e saída da API
├── domain
│   ├── entity                 entidades JPA
│   ├── enums / exception
│   └── repository             contratos de persistência (Spring Data)
│   └── storage                contrato de armazenamento de arquivos (StoragePort)
└── infrastructure
    ├── config                 regras de acesso, CORS, OpenAPI, tratamento global de erros
    ├── security               tokens JWT, hash de senha e criação do primeiro usuário
    └── storage                cliente HTTP do Supabase Storage
```

Toda resposta segue o envelope `{ "success", "message", "data" }`; erros de validação trazem
`errors: [{ "field", "message" }]`.

| Status | Quando |
|---|---|
| `400` | Corpo, parâmetro ou cabeçalho inválido; número fracionado em campo inteiro |
| `401` | Sem token, token inválido ou expirado, ou e-mail/senha incorretos no login |
| `404` | Imóvel, produto, foto, venda, hóspede, usuário, reserva ou canal não encontrado |
| `409` | Conflito com um registro existente: código duplicado, chave de idempotência reutilizada com outro conteúdo, total esperado divergente |
| `422` | Requisição válida, mas a regra de negócio não permite: estoque insuficiente, produto inativo, produto que não controla estoque, venda cancelada |

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/login` | Login com e-mail e senha; devolve o token 🌐 |
| GET | `/auth/me` | Dados do usuário dono do token 🔐 |
| PATCH | `/auth/senha` | Troca a própria senha 🔐 |
| POST | `/usuarios` | Cadastra um usuário 🔐 |
| GET | `/usuarios` | Lista os usuários 🔐 |
| GET | `/usuarios/{id}` | Consulta um usuário 🔐 |
| PUT | `/usuarios/{id}` | Atualiza nome e e-mail 🔐 |
| PATCH | `/usuarios/{id}/inativar` | Inativa e corta o acesso na hora (não vale para o próprio usuário) 🔐 |
| PATCH | `/usuarios/{id}/reativar` | Reativa 🔐 |
| PATCH | `/usuarios/{id}/senha` | Redefine a senha de outro usuário, sem pedir a atual 🔐 |
| GET | `/hospedes?busca=&incluirInativos=` | Lista hóspedes em ordem alfabética, com busca por nome ou documento 🔐 |
| GET | `/hospedes/{id}` | Consulta um hóspede 🔐 |
| POST | `/hospedes` | Cadastra um hóspede 🔐 |
| PUT | `/hospedes/{id}` | Atualiza os dados de um hóspede 🔐 |
| PATCH | `/hospedes/{id}/inativar` | Inativa 🔐 |
| PATCH | `/hospedes/{id}/reativar` | Reativa 🔐 |
| GET | `/hospedes/{id}/historico` | Reservas em que o hóspede é titular 🔐 |
| GET | `/uhs` | UHs (imóveis) com o status de governança calculado 🔐 |
| PATCH | `/uhs/{id}/governanca` | Define `LIVRE`, `SUJA` ou `MANUTENCAO` (422 `RN-07` se ocupada) 🔐 |
| GET | `/categorias` | Lista as categorias de UH 🔐 |
| POST | `/categorias` | Cadastra uma categoria 🔐 |
| PUT | `/categorias/{id}` | Atualiza nome, nível e descrição 🔐 |
| GET | `/tarifas` | Lista as tarifas, por início 🔐 |
| POST | `/tarifas` | Cadastra uma tarifa 🔐 |
| PUT | `/tarifas/{id}` | Atualiza uma tarifa 🔐 |
| GET | `/reservas?busca=&status=&canalId=&inicio=&fim=` | Lista reservas, da entrada mais recente para a mais antiga 🔐 |
| GET | `/reservas/cotacao?uhId=&checkin=&checkout=` | Cota a estadia pelas tarifas (RF-31, RF-32) 🔐 |
| GET | `/reservas/{id}` | Consulta uma reserva, com o histórico 🔐 |
| POST | `/reservas` | Cria uma reserva (status `RESERVADO`, valor da cotação) 🔐 |
| PUT | `/reservas/{id}` | Edita uma reserva ainda `RESERVADO` (recota) 🔐 |
| PUT | `/reservas/{id}/remanejamento` | Troca UH e/ou datas, inclusive com o hóspede na casa 🔐 |
| PUT | `/reservas/{id}/fnrh` | Preenche a FNRH 🔐 |
| POST | `/reservas/{id}/checkin` | Check-in: `RESERVADO` → `HOSPEDADO` 🔐 |
| POST | `/reservas/{id}/checkout` | Check-out: `HOSPEDADO` → `CHECKOUT_REALIZADO` (`{ autorizarPendencia }` opcional) 🔐 |
| POST | `/reservas/{id}/cancelamento` | Cancela com motivo e multa 🔐 |
| POST | `/reservas/{id}/cancelar` | Rota antiga do cancelamento, com corpo opcional 🔐 |
| POST | `/reservas/{id}/no-show` | Registra o no-show, com motivo e multa 🔐 |
| GET | `/reservas/{id}/conta` | Conta do hóspede 🔐 |
| POST | `/reservas/{id}/lancamentos` | Lança consumo na conta (só `HOSPEDADO`) 🔐 |
| POST | `/reservas/{id}/lancamentos/{lancamentoId}/estorno` | Estorna um lançamento 🔐 |
| POST | `/reservas/{id}/pagamentos` | Registra um recebimento na conta 🔐 |
| GET | `/bloqueios?inicio=&fim=` | Bloqueios ativos que cruzam o período 🔐 |
| POST | `/bloqueios` | Bloqueia uma UH 🔐 |
| PATCH | `/bloqueios/{id}/encerrar` | Encerra um bloqueio 🔐 |
| GET | `/painel/resumo?data=` | Resumo do dia (RF-55) 🔐 |
| GET | `/pessoas-do-dia?data=` | Hóspedes na casa na noite da data (RF-51 a RF-53) 🔐 |
| GET | `/configuracao` | Configuração da pousada 🔐 |
| PUT | `/configuracao` | Atualiza a configuração 🔐 |
| GET | `/financeiro/recebimentos?inicio=&fim=` | Pagamentos de reservas e de vendas avulsas 🔐 |
| GET | `/financeiro/repasses` | Repasses das OTAs 🔐 |
| PATCH | `/financeiro/repasses/{reservaId}/recebimento` | Concilia o repasse 🔐 |
| GET | `/financeiro/pendencias` | Contas com saldo 🔐 |
| GET | `/financeiro/fluxo?inicio=&fim=` | Fluxo de caixa realizado e previsto 🔐 |
| GET | `/despesas?inicio=&fim=` | Despesas por vencimento 🔐 |
| POST | `/despesas` | Cadastra uma despesa 🔐 |
| PATCH | `/despesas/{id}/pagamento` | Registra o pagamento da despesa 🔐 |
| GET | `/indicadores?inicio=&fim=` | Indicadores do período (módulo 8) 🔐 |
| GET | `/indicadores/previsao?inicio=&dias=` | Ocupação e receita já vendidas 🔐 |
| GET | `/canais?incluirInativos=` | Lista os canais de venda 🔐 |
| POST | `/canais` | Cadastra um canal 🔐 |
| PUT | `/canais/{id}` | Atualiza nome, tipo, comissão e cor 🔐 |
| PATCH | `/canais/{id}/inativar` | Inativa (some do formulário de nova reserva) 🔐 |
| PATCH | `/canais/{id}/reativar` | Reativa 🔐 |
| POST | `/imoveis` | Cria um imóvel 🔐 |
| GET | `/imoveis?status=&nome=&page=&size=&sort=` | Lista com filtros e paginação 🌐 |
| GET | `/imoveis/{id}` | Consulta um imóvel 🌐 |
| PUT | `/imoveis/{id}` | Atualiza (inclusive o status) 🔐 |
| PATCH | `/imoveis/{id}/inativar` | Inativa 🔐 |
| GET | `/imoveis/{id}/fotos` | Lista as fotos, na ordem de exibição 🌐 |
| POST | `/imoveis/{id}/fotos` | Cadastra uma foto por URL externa 🔐 |
| POST | `/imoveis/{id}/fotos/upload` | Envia uma imagem para o Storage 🔐 |
| PATCH | `/imoveis/{id}/fotos/{fotoId}/principal` | Define a foto principal 🔐 |
| DELETE | `/imoveis/{id}/fotos/{fotoId}` | Exclui uma foto 🔐 |
| POST | `/produtos` | Cadastra um produto 🔐 |
| GET | `/produtos?nome=&categoria=&ativo=&page=&size=&sort=` | Lista com filtros e paginação 🔐 |
| GET | `/produtos/{id}` | Consulta um produto 🔐 |
| PUT | `/produtos/{id}` | Atualiza (inclusive reativa, com `ativo: true`) 🔐 |
| PATCH | `/produtos/{id}/inativar` | Inativa 🔐 |
| GET | `/produtos/buscar?termo=&imovelId=&somenteAtivos=&limit=` | Busca dinâmica para autocompletar 🔐 |
| GET | `/imoveis/{id}/estoque?nome=&ativo=&page=&size=` | Saldo de cada produto neste imóvel 🔐 |
| GET | `/imoveis/{id}/estoque/alertas?page=&size=` | Produtos no mínimo ou abaixo dele 🔐 |
| PUT | `/imoveis/{id}/estoque/{produtoId}` | Define o estoque mínimo do produto no imóvel 🔐 |
| GET | `/imoveis/{id}/estoque/movimentacoes?produtoId=&tipo=&page=&size=` | Histórico de movimentações 🔐 |
| POST | `/imoveis/{id}/estoque/entradas` | Entrada de mercadoria 🔐 |
| POST | `/imoveis/{id}/estoque/ajustes` | Ajuste de inventário ou perda 🔐 |
| POST | `/estoque/transferencias` | Transfere um produto entre dois imóveis 🔐 |
| POST | `/pdv/vendas` | Registra uma venda avulsa e baixa o estoque do imóvel 🔐 |
| GET | `/pdv/vendas?imovelId=&status=&page=&size=` | Lista vendas, da mais recente para a mais antiga 🔐 |
| GET | `/pdv/vendas/{id}` | Consulta uma venda com os itens 🔐 |
| POST | `/pdv/vendas/{id}/cancelar` | Cancela a venda e devolve o estoque 🔐 |
| POST | `/pdv/vendas/{id}/devolucoes` | Devolução física de itens ao estoque 🔐 |
| POST | `/pdv/vendas/{id}/pagamento` | Registra o pagamento da venda (com troco em dinheiro) 🔐 |
| GET | `/actuator/health` | Health check 🌐 |

🌐 Pública. 🔐 Exige `Authorization: Bearer <token>`; sem ele, responde `401`.

## Autenticação

Login próprio da API: os usuários ficam na tabela `usuario`, com a senha guardada só como hash
BCrypt. O login devolve um token JWT (HS256, assinado com `JWT_SECRET`), que o cliente envia em
todas as rotas 🔐. Não há sessão nem cookie.

```bash
curl -X POST https://<api>/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@exemplo.com","senha":"sua-senha"}'
```

```json
{
  "success": true,
  "message": "Login realizado com sucesso",
  "data": {
    "token": "eyJ...",
    "tipo": "Bearer",
    "expiraEm": "2026-10-15T02:00:00Z",
    "usuario": { "id": "...", "nome": "Administrador", "email": "admin@exemplo.com", "ativo": true, "criadoEm": "...", "atualizadoEm": "..." }
  }
}
```

```bash
curl https://<api>/pdv/vendas -H "Authorization: Bearer eyJ..."
```

- **Primeiro acesso:** sem nenhum usuário não há como fazer login. Na subida, se a tabela
  `usuario` estiver vazia e `ADMIN_EMAIL` e `ADMIN_SENHA` estiverem definidas, o primeiro
  usuário é criado. Depois disso as variáveis são ignoradas: troque a senha por
  `PATCH /auth/senha` e, se quiser, remova `ADMIN_SENHA` do Render.
- **Outros usuários:** qualquer usuário logado cadastra, edita, inativa e reativa outros em
  `/usuarios`. Ainda não há perfis: todos têm o mesmo acesso. Usuários não são excluídos.
- **Inativação:** corta o acesso na hora. A cada requisição a API confere se o dono do token
  continua ativo, então o token de um usuário inativado é recusado (`401`) mesmo dentro da
  validade. Ninguém inativa o próprio usuário (`422`), o que garante que sempre reste um ativo.
- **Expiração:** o token vale `JWT_VALIDADE` (padrão 8 horas). Ao receber `401`, o cliente deve
  voltar para a tela de login. Não há refresh token nem logout no servidor. Trocar ou redefinir a
  senha não derruba tokens já emitidos; para derrubar todos de uma vez, troque `JWT_SECRET`.
- **Login errado:** e-mail inexistente, senha errada e usuário inativo respondem o mesmo `401`
  ("E-mail ou senha inválidos"), para não revelar quais e-mails estão cadastrados.
- **Token em rota pública:** se um token inválido ou expirado for enviado mesmo numa rota
  pública, a resposta é `401`. Não envie o cabeçalho quando não houver token.
- **Swagger:** use o botão **Authorize** com o token devolvido pelo login.

Imóveis e produtos não são excluídos fisicamente: não existe `DELETE /imoveis/{id}` nem
`DELETE /produtos/{id}`.

## Hóspedes

Cadastro de hóspedes (RF-20). Registros nunca são excluídos, só inativados (RN-15). Por
enquanto o cadastro tem só os campos da tela de listagem: nome, documento, nacionalidade e
telefone. O modelo `Hospede` do frontend também prevê data de nascimento, e-mail, endereço e
observações alimentares, que ainda não existem na API.

| Campo | Regra |
|---|---|
| `nome` | Obrigatório, até 150 caracteres |
| `tipoDocumento` | Obrigatório: `CPF`, `RG` ou `PASSAPORTE` |
| `numeroDocumento` | Opcional no cadastro (é exigido no check-in, RN-04). CPF precisa ter dígitos verificadores válidos e é guardado só com os dígitos; RG e passaporte, em maiúsculas. Sem documento, a resposta traz `""` (nunca `null`) |
| `nacionalidade` | Obrigatória, até 60 caracteres |
| `telefone` | Opcional, até 30 caracteres; em branco vira `null` |

- **Documento único:** o mesmo tipo e número não pode pertencer a dois hóspedes, ativos ou
  inativos. A comparação ignora pontuação e maiúsculas (`529.982.247-25` = `52998224725`).
  Conflito responde `409` com o nome de quem já tem o documento. Vários hóspedes podem ficar
  sem documento.
- **Busca:** `busca` procura parte do nome, sem diferenciar maiúsculas nem acentos, ou parte dos
  dígitos do documento. Sem `busca`, lista todos. A resposta é uma lista, sem paginação.
- **Histórico:** `GET /hospedes/{id}/historico` devolve as reservas em que o hóspede é titular,
  inclusive canceladas, no mesmo formato da listagem de reservas.
- **LGPD:** todas as rotas exigem token. A API devolve o documento completo; mascarar na
  listagem é tarefa da tela.

## UHs, categorias, tarifas e governança

A UH (unidade habitacional) é o **imóvel**: não há entidade separada. O imóvel ganhou:

| Campo | Observação |
|---|---|
| `tipo` | `CASA` ou `IMOVEL_COMPLETO`. Só pode haver um imóvel completo (`409` no segundo); uma UH com reservas ativas não muda de tipo (`422`) |
| `categoriaId` | Categoria da UH (RF-30); a tarifa é por categoria |
| `capacidadeAdultos`, `capacidadeCriancas` | RN-13. `capacidadeMaxima` passou a ser calculada (a soma) e não é mais gravada |
| `statusGovernanca` | Só em `GET /uhs`, já calculado |

- **Status do imóvel:** só `ATIVO` e `INATIVO`. O antigo `MANUTENCAO` virou governança: na
  migração V12, imóvel `MANUTENCAO` passou a `ATIVO` com governança `MANUTENCAO`.
- **Governança:** o banco guarda só `LIVRE`, `SUJA` ou `MANUTENCAO`. `OCUPADA` é calculada pelas
  reservas `HOSPEDADO` (com o imóvel completo hospedado, todas as casas saem `OCUPADA`) e não muda
  à mão (`422` `RN-07`). `BLOQUEADA` entra com os bloqueios.
- **Manutenção bloqueia reserva nova (`422` `RF-35`):** UH em `MANUTENCAO` não recebe reserva nem
  pode ser o destino de uma reserva editada. Vale nos dois sentidos do RN-02: com uma casa em
  manutenção o imóvel completo não é reservado, e com o imóvel completo em manutenção nenhuma casa é.
  Reservas que já estavam na UH continuam editáveis.
- **Horários:** `horarioCheckin`/`horarioCheckout` do imóvel são opcionais. Sem eles vale o padrão
  da tabela `configuracao` (14:00 e 12:00), e a resposta já traz o horário efetivo.
- **Imóvel completo sem endereço:** recebe o da primeira casa cadastrada. Casas exigem logradouro,
  cidade e UF.
- **Tarifas:** `fim` é a última noite (inclusive); `fim >= inicio`, `valorDiaria > 0` e
  `estadiaMinima >= 1` (`400` por campo). Tarifas da mesma categoria podem se sobrepor: em cada
  noite vale a de período mais curto.
- **Erros de regra (`422`)** trazem o campo `codigo` (`RN-07`, `RN-13`, `RN-15`...) quando a regra é
  do levantamento; a `message` não repete o código.

**Compatibilidade com o front em produção** (remover quando ele for atualizado): o imóvel ainda
aceita `capacidadeMaxima` sem `capacidadeAdultos` (vira capacidade de adultos; na edição, a de
crianças atual é mantida enquanto couber no total) e `status: "MANUTENCAO"` (vira `ATIVO` +
governança `MANUTENCAO`, inclusive no filtro `GET /imoveis?status=MANUTENCAO`). Campos da UH que
não vierem na edição ficam como estão.

## Reservas

A UH (unidade habitacional) de uma reserva é o **imóvel**: `uhId` é o id do imóvel e `uhNome`, o
nome dele. O valor vem da **cotação pelas tarifas** da categoria da UH; `valorTotal` no corpo é
aceito e ignorado, para o front antigo continuar funcionando.

```json
POST /reservas
{
  "uhId": "id-do-imóvel",
  "titularId": "id-do-hóspede",
  "canalId": "id-do-canal",
  "checkin": "2027-02-27",
  "checkout": "2027-03-06",
  "adultos": 2,
  "criancas": 0,
  "regimePensao": "CAFE_DA_MANHA",
  "codigoExterno": null,
  "observacoes": null
}
```

A resposta traz `codigo` (`CM-1001`...), os nomes (`uhNome`, `titularNome`, `canalNome`), `fnrh`,
`encerramento` (cancelamento e no-show), `pendenciaAutorizada` e, no detalhe e nas ações,
`historico` (`{ dataHora, descricao, usuario }`). Na listagem, `historico` vem nulo.

- **Período:** `[checkin, checkout)`. A noite do checkout não é ocupada. Bloqueios usam `fim` inclusive.
- **Cotação (RF-31, RF-32):** em cada noite vale a tarifa de período mais curto da categoria. Sem
  tarifa: `422 RF-31`. Venda fechada, chegada fechada na primeira noite, saída fechada no dia do
  checkout ou estadia mínima: `422 RF-32`.
- **Disponibilidade (RN-01, RN-02):** reservas ativas (nem canceladas nem no-show) e bloqueios ativos
  não se cruzam; o imóvel completo conflita com todas as casas e cada casa com ele. `422 RN-01`
  quando o conflito é na mesma UH e `RN-02` quando é na relacionada. As linhas da UH e do imóvel
  completo ficam travadas até o commit (RNF-09): de duas gravações simultâneas, uma passa.
- **Ordem das recusas ao criar:** UH inativa (`RN-15`) ou em manutenção (`RF-35`), titular inativo
  (`400`), cotação, capacidade (`RN-13`) e disponibilidade.
- **Ciclo (RN-03):** `RESERVADO` → `HOSPEDADO` → `CHECKOUT_REALIZADO`, ou `RESERVADO` → `CANCELADA` /
  `NO_SHOW`. Ação em status errado: `422 RN-03`. Check-in a partir da entrada (Brasília), com
  documento do titular e FNRH (`RN-04`) e a UH liberada pela governança (`RF-35`). Check-out com
  saldo exige `autorizarPendencia: true` (`RN-05`) e deixa a UH `SUJA` (`RN-07`).
- **Remanejamento:** `RESERVADO` ou `HOSPEDADO`; com o hóspede na casa a entrada não muda e a casa
  antiga fica `SUJA`. O valor é recotado.
- **Listagem:** `busca` procura no nome do hóspede, no código, no código externo e no nome da UH.
  `inicio`/`fim`: `checkout > inicio` e `checkin <= fim`. Resposta em lista, sem paginação.
- **Canais:** `comissao` (%) e `cor` (`#rrggbb`). A comissão é copiada para a reserva quando ela entra
  no canal; mudar o canal depois não muda reservas antigas.

## Conta, operação, financeiro e indicadores

- **Conta do hóspede:** `saldo = diarias − repasseCanal + consumos − pagamentos`; em reservas de OTA
  as diárias são recebidas do canal (RN-09). Lançamento com `produtoId` usa o preço do cadastro e
  baixa o estoque do imóvel da reserva; o estorno devolve. Pagamentos de reserva e de venda avulsa
  ficam na mesma tabela `pagamento`.
- **Painel e pessoas do dia:** calculados das reservas (RF-55, RF-51 a RF-53). Quem sai no dia não
  entra na lista de pessoas do dia (a validar com a pousada).
- **Financeiro:** recebimentos, despesas, repasses (previsão = checkout, a validar), pendências e
  fluxo de caixa. RN-08: de reserva cancelada só o valor retido entra como receita.
- **Indicadores:** noites contadas por casa (o imóvel completo vale uma em cada casa), receita
  apropriada noite a noite, período anterior de mesmo tamanho para comparação. Até 731 dias.

## Produtos

O produto é cadastrado uma única vez para toda a plataforma. A tabela `produto` não guarda
quantidade: o saldo será mantido por imóvel, em `estoque_imovel` (próxima etapa).

| Campo | Regra |
|---|---|
| `codigo` | Obrigatório, até 40 caracteres. Único sem diferenciar maiúsculas de minúsculas |
| `codigoBarras` | Opcional, até 50 caracteres. Único quando informado |
| `nome` | Obrigatório, até 150 caracteres |
| `descricao` | Opcional, até 2000 caracteres |
| `categoria` | Obrigatório: `CONSUMO`, `PASSEIO`, `TAXA` ou `SERVICO` |
| `unidade` | Obrigatório, até 10 caracteres; gravado em maiúsculas (`UN`, `KG`, `L`...) |
| `precoCusto` | Opcional, zero ou mais, até 2 casas decimais |
| `precoVenda` | Obrigatório, zero ou mais, até 2 casas decimais |
| `controlaEstoque` | Obrigatório. `false` para itens sem saldo físico, como taxas e passeios |
| `ativo` | Opcional. No cadastro o padrão é `true`; na atualização, sem o campo, não muda |

Código ou código de barras repetido responde `409` com a mensagem dizendo qual campo colidiu.

```bash
curl -X POST "$API/produtos" -H "Content-Type: application/json" -d '{
  "codigo": "COCA350",
  "codigoBarras": "7894900010015",
  "nome": "Coca-Cola Lata 350ml",
  "categoria": "CONSUMO",
  "unidade": "UN",
  "precoCusto": 3.20,
  "precoVenda": 6.00,
  "controlaEstoque": true
}'
```

## Busca dinâmica de produtos

`GET /produtos/buscar?termo=coca&imovelId={uuid}&limit=10` atende o autocompletar do cadastro,
do estoque e do PDV.

| Parâmetro | Obrigatório | Descrição |
|---|---|---|
| `termo` | sim | De 1 a 100 caracteres |
| `imovelId` | não | Casa cujo saldo acompanha os resultados. Sem ele, nenhum saldo é devolvido |
| `somenteAtivos` | não | Padrão `true`. Com `false`, inclui produtos inativos |
| `limit` | não | Padrão `10`. Acima de `PRODUTOS_BUSCA_LIMITE_MAXIMO` (50) é reduzido ao teto |

O termo é procurado em **qualquer parte do nome ou do código** e no **início do código de
barras**, sem diferenciar maiúsculas nem acentos: `c`, `coca` e `COLA` encontram "Coca-Cola",
`zero` encontra "Coca-Cola Zero" e `agua` encontra "Água Mineral". `%` e `_` digitados são
tratados como texto.

```json
{
  "success": true,
  "message": "Produtos encontrados",
  "data": [
    {
      "id": "uuid",
      "codigo": "COCA350",
      "codigoBarras": "7894900010015",
      "nome": "Coca-Cola Lata 350ml",
      "categoria": "CONSUMO",
      "unidade": "UN",
      "precoVenda": 6.00,
      "controlaEstoque": true,
      "ativo": true,
      "quantidadeDisponivel": 20,
      "semEstoque": false
    }
  ]
}
```

- `quantidadeDisponivel` é o saldo **somente do imóvel informado**. Vem `null` quando não há
  `imovelId` ou quando o produto não controla estoque (taxas, passeios, serviços).
- `semEstoque` é `true` para produto físico zerado naquela casa; serve para destacá-lo na tela.
- O preço de custo não é devolvido.

**Ordem dos resultados:** primeiro os disponíveis na casa (com saldo, ou que não controlam
estoque); dentro de cada grupo, código ou código de barras exato, depois nome que começa pelo
termo, depois palavra do nome que começa pelo termo, depois as demais ocorrências; por fim,
ordem alfabética.

A busca **não impede a venda** de um produto sem saldo: ela só informa. Quem recusa a venda sem
quantidade suficiente é o backend, no registro da venda.

### Por que um endpoint novo

`GET /produtos` já aceitava o filtro `nome`, mas não serve ao autocompletar: é paginado,
devolve o cadastro completo com o preço de custo, não conhece o saldo por imóvel e ordena por
coluna, não por relevância. Estender a listagem misturaria dois contratos em uma rota; por isso
a busca ficou em `/produtos/buscar` e a listagem não mudou. `buscar` é um caminho literal e não
conflita com `/produtos/{id}`, que só aceita UUID.

O filtro `nome` de `GET /produtos` e de `GET /imoveis/{id}/estoque` continua diferenciando
acentos; só a busca dinâmica os ignora.

### Como a busca é feita no banco

Filtro, ordenação e limite rodam em uma única consulta SQL parametrizada; nada é filtrado em
memória.

- **Acentos e maiúsculas:** a função `normalizar_busca()` (migração V7) usa `translate()` e
  `lower()`. A extensão `unaccent` foi avaliada e descartada: `unaccent()` é `STABLE`, então não
  pode ser usada em coluna gerada nem em índice sem um contorno que depende do schema onde a
  extensão está instalada, e esse schema é diferente no Supabase (`extensions`) e em um
  PostgreSQL comum (`public`). `translate()` é nativo, `IMMUTABLE` e cobre os acentos do
  português. As letras acentuadas maiúsculas estão no mapa para a busca funcionar também em
  bancos com `LC_CTYPE=C`.
- **Colunas geradas:** `produto.nome_busca` e `produto.codigo_busca` guardam o texto já
  normalizado e são mantidas pelo próprio banco.
- **Índices trigram (`pg_trgm`):** GIN em `nome_busca`, `codigo_busca` e `lower(codigo_barras)`.
  A extensão está disponível no Supabase e é criada no schema `extensions`. Se não puder ser
  criada, a migração registra um aviso e segue: a busca funciona sem os índices.

Tempo da consulta medido em PostgreSQL 17 local, com dados sintéticos:

| Termo | 2 mil produtos, sem índice | 50 mil, sem índice | 50 mil, com índice |
|---|---|---|---|
| 1 caractere (`c`) | 1 ms | 22 ms | 25 ms |
| 2 caracteres (`co`) | 6 ms | 50 ms | 48 ms |
| 4 caracteres (`coca`) | 9 ms | 65 ms | 13 ms |
| código de barras completo | 7 ms | 65 ms | 4 ms |
| termo inexistente | 9 ms | 65 ms | 0,3 ms |

Com 1 ou 2 caracteres o índice trigram não é usado (não há trigrama para extrair) e, de todo
modo, uma letra casa com boa parte do catálogo; o PostgreSQL percorre a tabela. Em um catálogo
de centenas ou poucos milhares de produtos isso leva milissegundos. O que protege o servidor
nesse caso é o `limit` com teto, não um índice.

## Estoque por imóvel

Cada imóvel tem o seu próprio saldo de cada produto, na tabela `estoque_imovel`
(`UNIQUE (imovel_id, produto_id)`). A Casa A pode ter 20 unidades e a Casa B 8 do mesmo produto;
toda consulta é restrita ao imóvel da rota e nunca devolve saldo de outra casa.

- **Listagem** (`GET /imoveis/{id}/estoque`): traz todos os produtos com `controlaEstoque: true`,
  em ordem de nome. Os que o imóvel nunca teve aparecem com `quantidadeAtual: 0` e
  `atualizadoEm: null`. Filtros: `nome` (parte do nome) e `ativo` (situação do produto). O
  parâmetro `sort` é ignorado.
- **Alertas** (`GET /imoveis/{id}/estoque/alertas`): produtos ativos com mínimo definido e saldo
  **igual ou inferior** a ele. Mínimo zero significa "sem alerta".
- **Estoque mínimo** (`PUT /imoveis/{id}/estoque/{produtoId}`, corpo `{"estoqueMinimo": 5}`): vale
  só para aquele imóvel e cria a linha de saldo, zerada, se ela ainda não existir. Produto sem
  controle de estoque responde `422`.

A quantidade é um número inteiro e **não pode ser alterada por estes endpoints**: ela só muda
por [movimentações](#movimentações-de-estoque). O banco recusa saldo negativo
(`CHECK (quantidade_atual >= 0)`).

Item da resposta:

```json
{
  "imovelId": "uuid",
  "produtoId": "uuid",
  "codigo": "COCA350",
  "codigoBarras": "7894900010015",
  "nome": "Coca-Cola Lata 350ml",
  "unidade": "UN",
  "produtoAtivo": true,
  "quantidadeAtual": 20,
  "estoqueMinimo": 5,
  "abaixoDoMinimo": false,
  "atualizadoEm": "2026-10-12T22:50:57.017667Z"
}
```

## Movimentações de estoque

Toda alteração de quantidade passa por um único ponto (`MovimentadorEstoque`), que bloqueia a
linha de saldo do produto no imóvel, confere o resultado e grava a movimentação na mesma
transação. Saldo e histórico mudam juntos ou não mudam.

| Operação | Corpo | Tipo gerado |
|---|---|---|
| `POST /imoveis/{id}/estoque/entradas` | `produtoId`, `quantidade`, `motivo` (opcional) | `ENTRADA` |
| `POST /imoveis/{id}/estoque/ajustes` | `produtoId`, `tipo`, `quantidade`, `motivo` (obrigatório) | `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO` ou `PERDA` |
| `POST /estoque/transferencias` | `imovelOrigemId`, `imovelDestinoId`, `produtoId`, `quantidade`, `motivo` (opcional) | `TRANSFERENCIA_SAIDA` na origem e `TRANSFERENCIA_ENTRADA` no destino |

Os tipos `SAIDA_VENDA` e `DEVOLUCAO` já existem, mas só serão gerados pelo PDV.

Regras:

- `quantidade` é um inteiro maior que zero; `1.5` responde `400`.
- Saída maior que o saldo **daquele imóvel** responde `422` com o disponível e o solicitado. O
  saldo de outra casa nunca é usado.
- Produto com `controlaEstoque: false` responde `422`. Produto inativo não recebe entrada, mas
  o saldo que sobrou pode ser ajustado, baixado ou transferido.
- A transferência é uma transação só: sem saldo na origem, nada muda em nenhuma das casas. As
  duas movimentações levam o mesmo `referenciaId` (o `transferenciaId` da resposta).

Movimentação na resposta:

```json
{
  "id": "uuid",
  "imovelId": "uuid",
  "produtoId": "uuid",
  "produtoCodigo": "COCA350",
  "produtoNome": "Coca-Cola Lata 350ml",
  "tipo": "ENTRADA",
  "quantidade": 24,
  "saldoAnterior": 0,
  "saldoPosterior": 24,
  "motivo": "Compra no mercado",
  "referenciaTipo": null,
  "referenciaId": null,
  "criadoEm": "2026-10-12T23:10:00.000000Z"
}
```

O histórico (`GET /imoveis/{id}/estoque/movimentacoes`) vem da movimentação mais recente para a
mais antiga e aceita os filtros `produtoId` e `tipo`.

### Histórico imutável

Não existe rota para alterar ou apagar uma movimentação, e o banco também recusa: um trigger em
`movimentacao_estoque` bloqueia `UPDATE`, `DELETE` e `TRUNCATE`. Um lançamento errado é corrigido
com um novo ajuste. Uma constraint confere a conta de cada linha (`saldo_posterior` = anterior
mais ou menos a quantidade, conforme o tipo).

### Concorrência

A linha de saldo é bloqueada (`SELECT ... FOR UPDATE`) antes de a quantidade ser lida, então duas
baixas simultâneas do mesmo produto na mesma casa são processadas uma depois da outra. A
transferência bloqueia os dois saldos sempre na mesma ordem, para que A→B e B→A simultâneas não
fiquem esperando uma pela outra.

Entradas, ajustes e transferências aceitam o cabeçalho opcional `Idempotency-Key` (veja
[Idempotência](#idempotência)). **Sem o cabeçalho**, repetir a mesma requisição (duplo clique,
nova tentativa após timeout) registra a movimentação duas vezes.

## PDV e vendas

`POST /pdv/vendas` registra uma venda avulsa em um imóvel:

```bash
curl -X POST "$API/pdv/vendas" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 3f6c1c1e-8f0a-4c56-9d6e-1b1f5f0c2a11" \
  -d '{
    "imovelId": "uuid-da-casa",
    "observacao": "Hóspede do quarto 2",
    "totalEsperado": 368.00,
    "itens": [
      { "produtoId": "uuid-coca", "quantidade": 3 },
      { "produtoId": "uuid-passeio", "quantidade": 1 }
    ]
  }'
```

- **Preços:** não há campo de preço na requisição. O preço de cada item é lido do cadastro no
  momento da venda, e o total é calculado pelo backend. Campos como `total` ou `precoUnitario`
  enviados pelo cliente são ignorados.
- **`totalEsperado` (opcional):** o total que o operador viu na tela. Se for diferente do total
  calculado (um preço mudou enquanto o carrinho estava aberto), a venda responde `409` em vez de
  cobrar outro valor.
- **Estoque:** cada item de produto com `controlaEstoque: true` gera uma `SAIDA_VENDA` no estoque
  **do imóvel da venda**. Se faltar saldo para qualquer item, responde `422` e nada é gravado:
  nem a venda, nem os itens, nem as baixas dos outros itens. O saldo de outra casa nunca é usado.
- **Preço histórico:** o item guarda código, nome e preço do produto no momento da venda.
  Alterar o cadastro depois não muda vendas já feitas.
- **Recusas (`422`):** imóvel inativo, produto inativo e o mesmo produto em dois itens.

Resposta:

```json
{
  "id": "uuid",
  "imovelId": "uuid",
  "tipo": "AVULSA",
  "status": "CONCLUIDA",
  "pagamentoStatus": "PENDENTE",
  "total": 368.00,
  "totalDevolvido": 0.00,
  "observacao": "Hóspede do quarto 2",
  "itens": [
    {
      "id": "uuid",
      "produtoId": "uuid",
      "produtoCodigo": "COCA350",
      "produtoNome": "Coca-Cola Lata 350ml",
      "quantidade": 3,
      "quantidadeDevolvida": 0,
      "precoUnitario": 6.00,
      "subtotal": 18.00
    }
  ],
  "criadoEm": "2026-10-12T23:30:00.000000Z",
  "canceladoEm": null,
  "motivoCancelamento": null
}
```

### Cancelamento e devolução

- **`POST /pdv/vendas/{id}/cancelar`** (corpo `{"motivo": "..."}`): devolve ao estoque do imóvel
  o que ainda não tinha sido devolvido e marca a venda como `CANCELADA`. Cancelar uma venda já
  cancelada não altera nada e devolve o estado atual, então o estoque nunca volta duas vezes.
- **`POST /pdv/vendas/{id}/devolucoes`** (corpo `{"motivo": "...", "itens": [{"produtoId",
  "quantidade"}]}`): devolução física, total ou parcial. Os produtos voltam ao estoque do imóvel
  da venda e `quantidadeDevolvida` do item aumenta. Responde `422` para quantidade maior que a
  vendida e ainda não devolvida, para produto que não é da venda, para item que não é produto
  físico e para venda cancelada. Exige `Idempotency-Key`.

As duas operações geram movimentações `DEVOLUCAO` com a venda como referência. A venda em si
nunca é apagada nem editada: não existe `PUT` nem `DELETE` em `/pdv/vendas/{id}`.

### Idempotência

`POST /pdv/vendas` e `POST /pdv/vendas/{id}/devolucoes` **exigem** o cabeçalho `Idempotency-Key`
(8 a 100 caracteres: letras, números, `-`, `_`, `.` ou `:`; um UUID serve). Entradas, ajustes e
transferências de estoque aceitam o mesmo cabeçalho, opcional.

O cliente gera uma chave nova para cada operação e reutiliza a mesma chave ao repetir a
requisição (duplo clique, nova tentativa após timeout):

- **Mesma chave, mesmo conteúdo:** a operação não é executada de novo. A resposta traz o
  resultado da primeira execução, com o cabeçalho `Idempotent-Replay: true`.
- **Mesma chave, conteúdo diferente:** `409`.
- **Operação que falhou** (por exemplo, `422` por falta de estoque): a chave não fica ocupada,
  porque é gravada na mesma transação da operação. A mesma chave pode ser usada na nova tentativa.
- **Requisições simultâneas com a mesma chave:** a segunda espera a primeira terminar e recebe
  o resultado dela.

As chaves ficam na tabela `chave_idempotencia` e hoje não expiram.

### O que ainda não existe

Os pontos abaixo dependem de módulos que não foram construídos. Nada foi simulado.

- **Pagamento:** a venda nasce `PENDENTE` e `POST /pdv/vendas/{id}/pagamento` (com `Idempotency-Key`)
  registra o pagamento de uma vez, pelo total menos o devolvido, com troco em dinheiro. Não há
  pagamento dividido nem reembolso; `totalDevolvido` é o valor dos itens devolvidos.
- **Consumo de hóspede:** vai para a conta da reserva (`POST /reservas/{id}/lancamentos`), não para
  o PDV. A coluna `venda.reserva_id` e o tipo `RESERVA` seguem sem uso.
- **Usuário:** a venda ainda não registra quem a fez.
- **Desconto, acréscimo e número sequencial de venda** não existem.

### Acesso

Produtos, estoque e PDV exigem token em qualquer ambiente, inclusive as rotas de leitura (a
listagem expõe o preço de custo). Veja [Autenticação](#autenticação).

### Contrato a conferir com o frontend

O frontend (Next.js, em outro repositório) já tem os conceitos `Produto`, `Venda`, `ItemVenda`
e `PdvGateway`, mas esses arquivos não estavam disponíveis quando este módulo foi escrito. O
contrato acima é uma proposta inicial e **não foi validado contra o frontend**. Pontos a comparar:

- Nomes dos campos (`codigo`, `codigoBarras`, `precoVenda`, `precoCusto`, `controlaEstoque`, `ativo`).
- Valores de `categoria` e formato de `unidade` (texto livre aqui; pode ser uma lista fixa lá).
- Se `codigo` é obrigatório no frontend ou gerado automaticamente.
- Formato da listagem (`data.content` paginado) e nomes dos filtros (`nome`, `categoria`, `ativo`).
- Se o frontend espera quantidade em estoque dentro do produto; aqui ela vem do estoque por imóvel.
- Se o frontend trabalha com quantidade fracionada (ex.: 1,5 kg); aqui a quantidade é inteira.
- Nomes dos campos do item de estoque (`quantidadeAtual`, `estoqueMinimo`, `abaixoDoMinimo`).
- A rota `PUT /imoveis/{id}/estoque/{produtoId}` não estava na lista proposta; foi criada porque
  não havia outra forma de definir o estoque mínimo.
- Entrada e ajuste recebem **um produto por requisição**; se o frontend lança uma compra com
  vários itens de uma vez, o contrato precisa mudar.
- O ajuste recebe a diferença (`tipo` + `quantidade`), não a quantidade contada no inventário.
- Nomes dos tipos de movimentação e dos campos (`saldoAnterior`, `saldoPosterior`, `referenciaId`).
- No PDV: o `PdvGateway` precisa enviar `Idempotency-Key`; confira os nomes de `Venda` e
  `ItemVenda` (`imovelId`, `itens`, `produtoId`, `quantidade`, `precoUnitario`, `subtotal`,
  `total`, `status`, `pagamentoStatus`), os valores de status e se o frontend envia forma de
  pagamento, desconto ou cliente, que este backend ainda não recebe.
- A rota `POST /pdv/vendas/{id}/devolucoes` não estava na lista proposta; foi criada para a
  devolução física pedida.
- Na busca: nomes dos parâmetros (`termo`, `imovelId`, `somenteAtivos`, `limit`), o campo
  `quantidadeDisponivel` nulo para itens sem estoque e o campo extra `semEstoque`.

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
| Sem token ou token inválido | 401 |

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

### Acesso

Upload, exclusão, foto principal e cadastro por URL exigem token, porque usam a secret key do
Supabase. A listagem de fotos é pública.

### Migrações

Ficam em `backend/src/main/resources/db/migration`. Uma migração já aplicada nunca é alterada:
mudanças de schema entram em um novo arquivo `V<n>__descricao.sql`. O Hibernate roda com
`ddl-auto=validate` e apenas confere se as entidades batem com o banco.

| Migração | Conteúdo |
|---|---|
| V1 | Imóveis, comodidades e fotos |
| V2 | Bloqueio do acesso pela Data API do Supabase e RLS |
| V3 | Fotos no Supabase Storage |
| V4 | Produtos |
| V5 | Estoque por imóvel |
| V6 | Movimentações de estoque e trigger de imutabilidade |
| V7 | Busca de produtos: função de normalização, colunas geradas e índices trigram |
| V8 | Vendas, itens de venda e chaves de idempotência |
| V9 | Usuários |
| V10 | Hóspedes |
| V11 | Canais (com os 4 iniciais) e reservas |
| V12 | Configuração da pousada, categorias de UH, tarifas; tipo, categoria, capacidades e governança no imóvel |

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
   `SUPABASE_SECRET_KEY`.
5. Defina `JWT_SECRET` (gere com `openssl rand -base64 48`; nunca reaproveite o do `.env` local)
   e, para o primeiro acesso, `ADMIN_EMAIL` e `ADMIN_SENHA`.
6. Faça o deploy, confira `https://<servico>.onrender.com/actuator/health` e faça login em
   `POST /auth/login`. Depois troque a senha por `PATCH /auth/senha`.

O deploy aplica as migrações pendentes ao iniciar. A V7 cria a extensão `pg_trgm` no schema
`extensions` do Supabase; se isso não for possível, ela registra um aviso no log e segue sem os
índices da busca.

No plano gratuito o serviço hiberna após um período sem requisições, e a primeira chamada
seguinte pode levar perto de um minuto.
