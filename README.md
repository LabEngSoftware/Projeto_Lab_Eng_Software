# Projeto_Lab_Eng_Software

Logística Dinâmica de Resgate de Perecíveis (Combate à Fome)

## Como rodar

Pré-requisitos: Java 21+ e Docker.

```bash
cd backend
./mvnw spring-boot:run   # sobe o Postgres do compose.yaml automaticamente (perfil dev)
./mvnw verify            # testes (usam H2 em memória, sem Docker)
```

Rodando pelo IntelliJ: na run configuration, defina **Working directory** como `$MODULE_WORKING_DIR$`
(pasta `backend/`), senão o Spring não encontra o `compose.yaml`.

Usuário admin de desenvolvimento: `admin@resgatai.local` / `admin12345`.

Frontend (Next.js como PWA; requer Node 20+), com o backend rodando:

```bash
cd frontend
npm install
npm run dev              # http://localhost:3000
```

O navegador chama `/api/*` no próprio Next, que repassa ao backend (`API_URL`, padrão `http://localhost:8080`),
então o backend não precisa de CORS. O token JWT fica no `localStorage`.

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

O prazo de retirada de uma reserva é a metade do tempo restante da janela do lote. Um worker roda a cada minuto
(`EXPIRACAO_CRON`, cron do Spring): reservas não retiradas até 15 minutos após o prazo expiram, a instituição perde
pontos de score e o lote volta às buscas; lotes disponíveis cuja janela de retirada acabou passam a `EXPIRADO`.

O pagamento (RF04) é simulado e liquidado na própria reserva: PIX é aprovado na hora; no cartão, número fora do
Luhn, cartão vencido ou terminado em `0002` são recusados (402) e o lote segue disponível. Só o final do cartão é
guardado. Retirada cancelada ou reserva expirada estornam o valor; entrega parcial estorna a parte não entregue.

Notificações (RF07) ficam na caixa de entrada (`GET /notificacoes`, consultada pelo PWA) e são enviadas por e-mail
por um worker a cada 30 segundos (`NOTIFICACAO_CRON`). Um lote novo notifica as instituições aprovadas num raio
que depende da urgência: 5 km (vence em menos de 6 h), 10 km (menos de 24 h) ou 20 km. Em desenvolvimento, o
Docker Compose sobe o [Mailpit](https://mailpit.axllent.org): os e-mails aparecem em http://localhost:8025.

Em produção defina `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres), `ADMIN_EMAIL` e `ADMIN_SENHA`.
Para enviar e-mails, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` e `MAIL_REMETENTE`;
sem eles as notificações ficam só na caixa de entrada.

### Produção (Docker)

```bash
cp .env.exemplo .env     # preencher: domínio, RDS, JWT_SECRET, admin
docker compose -f docker-compose.prod.yml up -d --build
```

Sobe backend, frontend e o [Caddy](https://caddyserver.com), único serviço exposto (portas 80 e 443), que emite
o certificado HTTPS do `DOMINIO` sozinho — PWA, service worker e câmera exigem HTTPS. O banco é externo (RDS).

### CI/CD na AWS (Learner Lab)

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

Os Atores do Sistema
O Doador (Mercado/Padaria): O usuário que tem o alimento. Ele não tem tempo. Se o processo de doação exigir mais do que três cliques, ele desiste e joga a comida fora.

O Receptor (ONG/Abrigo): O usuário que precisa do alimento. Operam com recursos escassos, dependem de voluntários para o transporte e precisam de previsibilidade.

O Motor de Roteamento (Seu Backend): O "ator" silencioso que cruza dados geográficos, tempo de validade e gerencia as transações concorrentes para garantir que ninguém perca a viagem.

O Fluxo Lógico e a Máquina de Estados
A vida útil de uma doação dentro do sistema não é apenas um registro no banco de dados, é uma máquina de estados finitos rigorosa.

Estado 1: Criação (Ingestão Rápida). O Doador abre o sistema e informa: "Lote de 50 pães franceses, validade em 4 horas". O sistema registra o lote com o status DISPONIVEL.

Estado 2: O Roteamento e Notificação. O backend calcula um raio de distância baseado no tempo de validade. Alimentos que vencem em 2 horas só notificam ONGs a 5km. Alimentos que vencem em 12 horas podem notificar ONGs a 20km.

Estado 3: A Corrida Crítica (Reserva). Múltiplas ONGs recebem o push. Duas apertam "Aceitar Lote" no mesmo milissegundo. Aqui entra o coração da sua engenharia: o sistema aplica um Lock transacional (otimista ou pessimista). Apenas uma requisição vence. O estado do lote muda para RESERVADO_PENDENTE_RETIRADA. A ONG perdedora recebe um erro claro informando que o lote já foi pego.

Estado 4: Timeout de Abandono (Job Assíncrono). Se a ONG reservou, mas não buscou em um tempo pré-determinado (ex: 50% do tempo de validade restante), um worker em background expira a reserva, penaliza a ONG (diminuindo seu score futuro) e volta o lote para DISPONIVEL.

Estado 5: O Handshake (Conclusão). A ONG chega ao local, lê um QR Code simples na tela do Doador ou insere um PIN de 4 dígitos. A transação atômica é efetivada. Lote muda para COLETADO.

Funcionalidades Core (Visão Modular)
Para não construir um monólito engessado, o domínio da aplicação deve ser dividido em módulos com responsabilidades únicas.

Gestão de Inventário e Validade: Módulo responsável apenas por cadastrar os lotes e monitorar o tempo de expiração de forma contínua.

Motor de Matchmaking Geoespacial: Módulo isolado que recebe coordenadas e retorna a lista de ONGs elegíveis, priorizando as que têm maior índice de coleta bem-sucedida ou menor distância.

Gerenciador de Concorrência e Reservas: A camada de infraestrutura que gerencia a fila de requisições, garantindo que o banco de dados nunca registre uma dupla reserva.

Sistema de Reputação e Punição (Score): Funcionalidade para lidar com o comportamento humano. Doadores que cancelam muito ou ONGs que reservam e não buscam perdem prioridade no algoritmo.

O Teste de Estresse (Perspectivas e Contrapontos)
Como seu parceiro de projeto, preciso apontar as falhas potenciais nessa lógica para você blindar sua arquitetura antes de escrever a primeira linha de código:

A Ilusão da Coleta Imediata: Estamos assumindo que a ONG tem um carro abastecido e um motorista a postos o tempo todo. A realidade: A logística é demorada. A sua lógica de roteamento precisa cruzar a distância com o trânsito médio, senão o alimento estraga no porta-malas do voluntário.

O Risco do "No-Show": O que acontece quando a ONG reserva e não aparece? O Doador fica frustrado e nunca mais usa o app. Seu sistema precisa de alertas escalonados. Se a ONG não validar o início da viagem em X minutos, a reserva deve cair automaticamente.

O Problema do Banco de Dados Travado: Se você usar as transações padrão do banco de dados relacional para lidar com a concorrência na hora da reserva, um pico de acessos pode gerar deadlocks ou derrubar o banco. Você precisará pensar se usará filas em memória, estratégias de banco adequadas, ou se essa validação ocorrerá antes de bater no disco.
