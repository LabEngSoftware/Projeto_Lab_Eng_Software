#!/bin/bash
# Prepara a EC2 do Jenkins (aula 7) para também rodar a aplicação. Rodar uma vez, na EC2, depois do Elastic IP:
#   sudo bash ec2-setup.sh <endpoint-do-rds> <senha-do-rds> <email-do-admin> <senha-do-admin>
set -euo pipefail
[ $# -eq 4 ] || { sed -n 3p "$0"; exit 1; }
RDS="$1" RDS_SENHA="$2" ADMIN_EMAIL="$3" ADMIN_SENHA="$4"

# JDK (o script da aula só instala o JRE, e o mvnw precisa do javac) + Docker com o plugin compose
apt-get update
apt-get install -y openjdk-21-jdk-headless docker.io docker-compose-v2
usermod -aG docker jenkins

# 2 GB de swap: testes do Spring + build do Next + Jenkins no mesmo host
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# Endereço público (o Elastic IP) → domínio sslip.io, para o Caddy emitir o certificado HTTPS
TOKEN=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
IP=$(curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)

# Lido pelo Jenkinsfile; fora do workspace para sobreviver aos builds. Aspas simples: o compose não interpola.
ENV_FILE=/var/lib/jenkins/resgatai.env
cat > "$ENV_FILE" <<EOF
DOMINIO=${IP//./-}.sslip.io
SPRING_DATASOURCE_URL='jdbc:postgresql://$RDS:5432/postgres'
SPRING_DATASOURCE_USERNAME='postgres'
SPRING_DATASOURCE_PASSWORD='$RDS_SENHA'
JWT_SECRET='$(openssl rand -base64 48)'
ADMIN_EMAIL='$ADMIN_EMAIL'
ADMIN_SENHA='$ADMIN_SENHA'
EOF
chown jenkins:jenkins "$ENV_FILE"
chmod 600 "$ENV_FILE"

systemctl restart jenkins # para o jenkins enxergar o grupo docker
echo "Pronto. App em https://${IP//./-}.sslip.io depois do primeiro build no Jenkins."
