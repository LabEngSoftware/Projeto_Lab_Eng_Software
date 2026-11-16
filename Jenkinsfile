// Job "Pipeline script from SCM" na branch main, disparado pelo webhook do GitHub.
// O agente é a própria EC2 da aplicação (infra/ec2-setup.sh): JDK 21 + Docker, usuário jenkins no grupo docker.
pipeline {
    agent any

    environment {
        // Fora do workspace e do git: criado pelo infra/ec2-setup.sh
        ENV_FILE = '/var/lib/jenkins/resgatai.env'
        COMPOSE = "docker compose -p resgatai --env-file ${ENV_FILE} -f docker-compose.prod.yml"
    }

    stages {
        stage('Backend: testes') {
            steps {
                dir('backend') {
                    sh './mvnw -B clean verify'
                }
            }
        }

        // O build da imagem do front roda lint, checagem de tipos e o next build (frontend/Dockerfile).
        stage('Build das imagens') {
            steps {
                sh "${COMPOSE} build"
            }
        }

        stage('Deploy') {
            steps {
                sh "${COMPOSE} up -d --remove-orphans"
            }
        }

        stage('Verificação') {
            steps {
                // Passa por Caddy (HTTPS) → frontend (/api) → backend → banco
                sh '''
                    DOMINIO=$(grep '^DOMINIO=' "$ENV_FILE" | cut -d= -f2)
                    curl -fsS --retry 30 --retry-delay 5 --retry-all-errors "https://$DOMINIO/api/actuator/health"
                '''
            }
        }
    }

    post {
        always {
            junit 'backend/target/surefire-reports/*.xml'
        }
        success {
            sh 'docker image prune -f' // a cada deploy sobram as imagens antigas; o disco da EC2 é pequeno
        }
    }
}
