pipeline {
    agent none  // ❗ 전체적으로 실행 노드를 할당하지 않음

    stages {
        stage('Checkout') {
            agent any  // ✅ Checkout 단계에서는 노드 할당 필요
            steps {
                git branch: 'dev', url: 'https://github.com/kwantke/lock_study.git'
            }
        }
        stage('Gradle Build') {
            agent any
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew dependencies --no-daemon'
                sh './gradlew clean build -x test --no-daemon --stacktrace'
            }
        }
        stage('Docker Build') {
            when {
                expression { params.DOCKER_BUILD == true }
            }
            agent any  // ✅ Docker 빌드를 실행할 노드 지정
            steps {
                script {
                    if (params.DOCKER_IMAGE_TAG != "") {
                        echo "[docker image tag is not null]"
                        imageTag = params.DOCKER_IMAGE_TAG
                    } else {
                        echo "[docker image tag is null]"
                        imageTag = env.BUILD_NUMBER
                    }
                }
                sh """
                    docker build --build-arg ENVIRONMENT=${params.ENVIRONMENT} -t kwangko/test1:${params.ENVIRONMENT}_${imageTag} .
                """
            }
        }
        stage('Docker Push') {
            when {
                expression { params.DOCKER_PUSH == true }
            }
            agent any  // ✅ Docker Push 실행할 노드 지정
            steps {
                withDockerRegistry(credentialsId: 'dockerhub-signin', url: 'https://index.docker.io/v1/') {
                    sh "docker push kwangko/test1:${params.ENVIRONMENT}_${imageTag}"
                }
            }
        }
    }
}
