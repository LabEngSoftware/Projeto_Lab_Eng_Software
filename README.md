# ResgatAI

Logística dinâmica de resgate de perecíveis (combate à fome). Mercados e padarias publicam o excedente de alimentos
perto do vencimento; ONGs, abrigos e cozinhas solidárias próximas são avisadas, reservam o lote e retiram com QR Code
ou PIN.

**App no ar:** https://3-222-213-101.sslip.io (AWS Learner Lab; fica fora do ar quando a sessão do lab está desligada)

Projeto da disciplina Laboratório de Engenharia de Software (Mackenzie), grupo **Biri Dins**:

- Arthur Roldan Slikta
- Felipe Haddad
- Gabriel Kondo
- João Pedro Bocchini
- Lucas Brezolini

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4 (Web, Security com JWT, Data JPA), Flyway |
| Banco | PostgreSQL (Amazon RDS em produção; H2 nos testes) |
| Frontend | Next.js 16 + React 19 como PWA, mapa com Leaflet/OpenStreetMap |
| Infra | Docker Compose, Caddy (HTTPS automático), Jenkins, AWS EC2 + RDS |

```
backend/     API Spring Boot (pacote resgatai)
frontend/    PWA Next.js
infra/       setup da EC2 e seed de dados de demonstração
Jenkinsfile  pipeline de CI/CD
PROMPTS.md   prompts usados com o assistente de IA durante o desenvolvimento
```

## Como funciona

**Atores**
- **Doador** (mercado, padaria, hortifruti): publica lotes em poucos toques e valida a retirada.
- **Instituição** (ONG, abrigo, cozinha solidária): vê os lotes próximos no mapa, reserva e retira. Só usa o
  sistema depois de aprovada pelo administrador.
- **Administrador**: aprova ou rejeita instituições, gerencia usuários e consulta a auditoria.

**Ciclo de vida do lote**

1. **Disponível**: o doador publica o lote com validade e janela de retirada.
2. **Notificação**: o backend avisa as instituições aprovadas num raio que depende da urgência: 5 km se vence em
   menos de 6 h, 10 km em menos de 24 h e 20 km nos demais casos.
3. **Reserva** (`RESERVADO_PENDENTE`): a reserva trava o registro do lote no banco (lock pessimista), então, se duas
   instituições reservam ao mesmo tempo, só uma vence e a outra recebe um erro claro (409). Lote com preço simbólico
   exige pagamento simulado (PIX ou cartão). A instituição recebe um QR Code e um PIN de 4 dígitos.
4. **Expiração**: o prazo de retirada é metade do tempo restante da janela. Sem retirada até 15 min depois do prazo,
   um worker expira a reserva, tira 10 pontos do score da instituição e o lote volta a ficar disponível. Lotes cuja
   janela acaba sem reserva viram `EXPIRADO`.
5. **Retirada** (`COLETADO`): o doador lê o QR Code ou digita o PIN da instituição, confere o lote e confirma a
   entrega (inteira ou parcial). O doador ganha 5 pontos de score, e a instituição também, se retirou no prazo.
   Se o lote não confere, o doador
   recusa a entrega (`CANCELADO`) e o pagamento é estornado.

## Como rodar

Pré-requisitos: Java 21+, Node 20+ e Docker.

```bash
cd backend
./mvnw spring-boot:run   # sobe o Postgres do compose.yaml automaticamente (perfil dev)
./mvnw verify            # testes (usam H2 em memória, sem Docker)
```

Rodando pelo IntelliJ: na run configuration, defina **Working directory** como `$MODULE_WORKING_DIR$`
(pasta `backend/`), senão o Spring não encontra o `compose.yaml`.

Usuário admin de desenvolvimento: `admin@resgatai.local` / `admin12345`.

Frontend, com o backend rodando:

```bash
cd frontend
npm install
npm run dev              # http://localhost:3000
```

O navegador chama `/api/*` no próprio Next, que repassa ao backend (`API_URL`, padrão `http://localhost:8080`),
então o backend não precisa de CORS. O token JWT fica no `localStorage`. Para mexer só no front usando o backend
da AWS: `API_URL=https://3-222-213-101.sslip.io/api npm run dev`.

### Dados de demonstração

```bash
python3 infra/seed.py http://localhost:8080 admin@resgatai.local admin12345          # local
python3 infra/seed.py https://3-222-213-101.sslip.io/api <email-admin> <senha-admin>  # produção
```

Cria doadores, instituições e lotes em São Paulo, todos com a senha `resgatai123`:

| Perfil | E-mail |
|---|---|
| Doador | `padaria@resgatai.demo`, `mercado@resgatai.demo`, `hortifruti@resgatai.demo` |
| Instituição aprovada | `ong@resgatai.demo`, `cozinha@resgatai.demo` |
| Instituição aguardando aprovação | `abrigo@resgatai.demo` |

Os lotes vencem em horas ou dias: rode de novo antes de uma demonstração.

## API

| Endpoint | Acesso | Descrição |
|---|---|---|
| `POST /auth/registro` | público | Cadastro de Estabelecimento ou Instituição (instituição entra como `PENDENTE`) |
| `POST /auth/login` | público | Retorna `{ "token": "<JWT>" }` |
| `POST /auth/definir-senha` | público | Primeiro acesso: `{ token, senha, aceiteLgpd }` (link gerado pelo admin) |
| `GET /auth/me` | autenticado | Dados do usuário logado (`Authorization: Bearer <JWT>`) |
| `GET /admin/usuarios` | admin | Busca paginada; filtros `nome`, `documento`, `perfil`, `ativo`, `statusAnalise` |
| `GET /admin/usuarios/{id}` | admin | Consultar usuário |
| `POST /admin/usuarios` | admin | Cadastrar usuário; o link de primeiro acesso vai por e-mail (sem SMTP, volta em `linkPrimeiroAcesso`) |
| `PUT /admin/usuarios/{id}` | admin | Atualizar nome, e-mail, telefone e endereço |
| `DELETE /admin/usuarios/{id}` | admin | Inativar (o token do usuário deixa de valer na hora) |
| `POST /admin/usuarios/{id}/aprovar` | admin | Aprovar instituição (RF06) |
| `POST /admin/usuarios/{id}/rejeitar` | admin | Rejeitar instituição: `{ motivo }` |
| `GET /admin/auditoria?usuarioId=` | admin | Log de auditoria |
| `POST /lotes` | estabelecimento | Publicar lote (UC02); `enderecoRetirada` opcional para retirar em outro endereço |
| `PUT /lotes/{id}/foto` | estabelecimento | Foto do lote (multipart, campo `arquivo`; JPEG/PNG/WebP até 5 MB) |
| `GET /lotes/meus` | estabelecimento | Lotes do próprio doador, em qualquer status |
| `GET /lotes` | autenticado | Busca de lotes disponíveis (RF03); filtros `nome`, `categoria`, `precoMin`, `precoMax`, `venceAte`, `raioKm` (padrão 10) e `latitude`/`longitude` (padrão: endereço do usuário) |
| `GET /lotes/{id}` | autenticado | Consultar lote, com `distanciaKm` até o usuário |
| `GET /lotes/{id}/foto` | público | Imagem do lote |
| `POST /lotes/{id}/reserva` | instituição aprovada | Reservar lote (UC04): lock no registro, só a primeira requisição vence (as demais recebem 409); retorna `pin` e `qrCode`. Lote com preço exige `metodo` (`PIX` ou `CARTAO` + `cartao`) |
| `GET /reservas` | autenticado | Instituição: as próprias reservas, com PIN. Estabelecimento: reservas dos seus lotes, sem PIN |
| `POST /retiradas/conferencia` | estabelecimento | Conferir a reserva pelo `codigo` (conteúdo do QR Code ou PIN digitado) antes da entrega (UC06) |
| `POST /retiradas` | estabelecimento | Validar a retirada: `codigo`, `quantidadeEntregue` opcional (entrega parcial) e `autorizarAtraso` (fora do prazo); retorna o comprovante |
| `POST /retiradas/cancelamento` | estabelecimento | Recusar a entrega por divergência: `codigo` e `motivo` |
| `GET /impacto` | autenticado | Retiradas concluídas e quantidade resgatada por unidade, da plataforma e do usuário |
| `GET /notificacoes` | autenticado | Caixa de entrada do usuário (novos lotes próximos, reservas, retiradas, expirações) |
| `PUT /notificacoes/{id}/lida` | autenticado | Marcar notificação como lida |
| `GET /actuator/health` | público | Health check |

Endereços são geocodificados no cadastro pela API pública do [Nominatim](https://nominatim.org) (OpenStreetMap);
nos testes ela é simulada.

O worker de expiração roda a cada minuto (`EXPIRACAO_CRON`, cron do Spring).

O pagamento (RF04) é simulado e liquidado na própria reserva: PIX é aprovado na hora; no cartão, número fora do
Luhn, cartão vencido ou terminado em `0002` são recusados (402) e o lote segue disponível. Só o final do cartão é
guardado. Retirada cancelada ou reserva expirada estornam o valor; entrega parcial estorna a parte não entregue.

Notificações (RF07) ficam na caixa de entrada (`GET /notificacoes`, consultada pelo PWA) e são enviadas por e-mail
por um worker a cada 30 segundos (`NOTIFICACAO_CRON`). Em desenvolvimento, o Docker Compose sobe o
[Mailpit](https://mailpit.axllent.org): os e-mails aparecem em http://localhost:8025.

Em produção defina `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres), `ADMIN_EMAIL` e `ADMIN_SENHA`.
Para enviar e-mails, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e `MAIL_REMETENTE`;
sem eles as notificações ficam só na caixa de entrada.

## Produção (Docker)

```bash
cp .env.exemplo .env     # preencher: domínio, RDS, JWT_SECRET, admin
docker compose -f docker-compose.prod.yml up -d --build
```

Sobe backend, frontend e o [Caddy](https://caddyserver.com), único serviço exposto (portas 80 e 443), que emite
o certificado HTTPS do `DOMINIO` sozinho — PWA, service worker e câmera exigem HTTPS. O banco é externo (RDS).

## CI/CD na AWS (Learner Lab)

Uma EC2 roda o Jenkins e a aplicação; o banco fica no RDS. A cada push na `main`, o webhook do GitHub dispara o
`Jenkinsfile`: testes do backend → build das imagens (inclui lint e build do front) → `docker compose up -d` →
verificação do `/api/actuator/health` pela URL pública.

1. **RDS** (Aurora and RDS → Create database): PostgreSQL, template *Free tier*, identificador `resgatai`,
   usuário `postgres`, senha própria (sem `'`), `db.t3.micro`. Em *Connectivity*, escolha **Connect to an EC2
   compute resource** e selecione a EC2 do Jenkins (o console libera a porta 5432 só para ela). Em *Additional
   configuration*, desmarque *Enhanced monitoring* (o Learner Lab não permite). Anote o *Endpoint*.
2. **EC2 do Jenkins**: *Stop* → *Actions › Instance settings › Change instance type* → `t3.medium`; em *Storage*,
   clique no volume → *Modify* → 20 GB; *Start*. Em *Elastic IPs*: *Allocate* e *Associate* com a instância.
   No Security Group dela, adicione as regras de entrada HTTP (80) e HTTPS (443) de `0.0.0.0/0`.
3. **Webhook**: no GitHub, *Settings › Webhooks*, troque o endereço por `http://<elastic-ip>:8080/github-webhook/`.
4. **Preparar a EC2** (instala JDK 21, Docker e swap; gera o `/var/lib/jenkins/resgatai.env`):
   ```bash
   scp -i ~/.ssh/labsuser.pem infra/ec2-setup.sh ubuntu@<elastic-ip>:
   ssh -i ~/.ssh/labsuser.pem ubuntu@<elastic-ip> sudo bash ec2-setup.sh <endpoint-do-rds> <senha-do-rds> <email-admin> <senha-admin>
   ```
5. **Jenkins** (`http://<elastic-ip>:8080`): *New Item* → nome `resgatai`, tipo **Pipeline**. Marque *GitHub project*
   (URL do repositório) e *GitHub hook trigger for GITScm polling*. Em *Pipeline*: *Pipeline script from SCM*, Git,
   `https://github.com/LabEngSoftware/Projeto_Lab_Eng_Software.git`, branch `*/main`, script `Jenkinsfile`.
   Desative o job antigo do tipo Maven, senão o webhook dispara os dois. *Build Now*.
6. **Dados de exemplo**: `python3 infra/seed.py https://<ip-com-hífens>.sslip.io/api <email-admin> <senha-admin>`.

O app fica em `https://<ip-com-hífens>.sslip.io` (ex.: Elastic IP `54.1.2.3` → `54-1-2-3.sslip.io`). Quando a
sessão do Learner Lab acaba, EC2 e RDS param; ao iniciar o lab de novo, confira se o RDS voltou a *Available*.
Os containers e o Jenkins sobem sozinhos.
