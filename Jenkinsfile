pipeline {
    agent any

    stages {
        stage('Backend: Build & Test') {
            steps {
                dir('backend') {
                    sh './mvnw -B clean verify'
                }
            }
        }
    }

    post {
        always {
            junit 'backend/target/surefire-reports/*.xml'
        }
    }
}
