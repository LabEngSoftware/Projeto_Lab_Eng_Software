"""Dados de exemplo para a apresentação, criados pela própria API (passam pelas mesmas validações e geocodificação).

    python3 infra/seed.py https://1-2-3-4.sslip.io/api admin@exemplo.com senha-do-admin
    python3 infra/seed.py http://localhost:8080 admin@resgatai.local admin12345   # desenvolvimento

Rodar de novo é seguro: usuários já existentes são reaproveitados e só os lotes são criados outra vez.
Os lotes vencem em horas/dias, então rode no dia da apresentação.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE, ADMIN_EMAIL, ADMIN_SENHA = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]
SENHA = "resgatai123"

ESTABELECIMENTOS = [
    ("Padaria Pão da Consolação", "padaria@resgatai.demo", "Rua da Consolação, 930, São Paulo"),
    ("Mercado Paulista", "mercado@resgatai.demo", "Avenida Paulista, 1578, São Paulo"),
    ("Hortifruti Augusta", "hortifruti@resgatai.demo", "Rua Augusta, 1500, São Paulo"),
]
INSTITUICOES = [  # (nome, e-mail, endereço, aprovar)
    ("ONG Prato Cheio", "ong@resgatai.demo", "Rua Maria Antônia, 294, São Paulo", True),
    ("Cozinha Solidária Angélica", "cozinha@resgatai.demo", "Avenida Angélica, 2000, São Paulo", True),
    ("Abrigo Frei Caneca", "abrigo@resgatai.demo", "Rua Frei Caneca, 569, São Paulo", False),  # fila de aprovação
]
LOTES = {  # e-mail do doador: [(nome, categoria, quantidade, unidade, preço, horas até vencer)]
    "padaria@resgatai.demo": [
        ("Pão francês", "PADARIA", 50, "UNIDADE", 0, 5),
        ("Bolo de fubá", "PADARIA", 4, "UNIDADE", 6.50, 20),
        ("Sonho de creme", "PADARIA", 12, "UNIDADE", 0, 30),
    ],
    "mercado@resgatai.demo": [
        ("Iogurte natural", "LATICINIOS", 24, "UNIDADE", 1.20, 48),
        ("Frango resfriado", "CARNES", 8, "KG", 9.90, 22),
        ("Arroz e feijão (embalagem avariada)", "MERCEARIA", 10, "PACOTE", 0, 96),
    ],
    "hortifruti@resgatai.demo": [
        ("Banana prata", "HORTIFRUTI", 15, "KG", 0, 40),
        ("Tomate", "HORTIFRUTI", 6, "KG", 2.00, 26),
    ],
}


def api(metodo, caminho, corpo=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    dados = json.dumps(corpo).encode() if corpo is not None else None
    with urllib.request.urlopen(urllib.request.Request(BASE + caminho, dados, headers, method=metodo)) as r:
        texto = r.read()
        return json.loads(texto) if texto else None


def cnpj(i):
    n = [int(c) for c in f"{52847100 + i:08d}0001"]
    for pesos in ([5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]):
        resto = sum(a * b for a, b in zip(n, pesos)) % 11
        n.append(0 if resto < 2 else 11 - resto)
    return "".join(map(str, n))


def registrar(i, perfil, nome, email, endereco):
    """Devolve o id se criou agora, None se já existia."""
    try:
        u = api("POST", "/auth/registro", {
            "perfil": perfil, "nome": nome, "documento": cnpj(i), "email": email, "senha": SENHA,
            "telefone": f"(11) 9{i:04d}-0000", "endereco": endereco, "aceiteLgpd": True})
        print("criado:", nome)
        return u["id"]
    except urllib.error.HTTPError as e:
        if e.code != 409:
            raise SystemExit(f"{nome}: {e.code} {e.read().decode()}")
        print("já existia:", nome)
    finally:
        time.sleep(1.1)  # cada cadastro geocodifica no Nominatim, que limita a 1 requisição por segundo


def login(email, senha):
    return api("POST", "/auth/login", {"email": email, "senha": senha})["token"]


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


admin = login(ADMIN_EMAIL, ADMIN_SENHA)
for i, (nome, email, endereco) in enumerate(ESTABELECIMENTOS):
    registrar(i, "ESTABELECIMENTO", nome, email, endereco)
for i, (nome, email, endereco, aprovar) in enumerate(INSTITUICOES, start=len(ESTABELECIMENTOS)):
    id_ = registrar(i, "INSTITUICAO", nome, email, endereco)
    if id_ and aprovar:
        api("POST", f"/admin/usuarios/{id_}/aprovar", token=admin)

agora = datetime.now(timezone.utc)
for email, lotes in LOTES.items():
    token = login(email, SENHA)
    for nome, categoria, quantidade, unidade, preco, horas in lotes:
        validade = agora + timedelta(hours=horas)
        api("POST", "/lotes", {
            "nome": nome, "categoria": categoria, "quantidade": quantidade, "unidade": unidade, "preco": preco,
            "validade": iso(validade), "retiradaInicio": iso(agora), "retiradaFim": iso(validade - timedelta(hours=1)),
            "observacoes": "Dados de exemplo"}, token)
        print("lote:", nome)

print(f"\nPronto. Logins (senha {SENHA}):", *[e for _, e, *_ in ESTABELECIMENTOS + INSTITUICOES], sep="\n  ")
