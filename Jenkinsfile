pipeline {
    agent none  // ❗ 전체적으로 실행 노드를 할당하지 않음

    stages {
        stage('Checkout') {
            agent any  // ✅ Checkout 단계에서는 노드 할당 필요
            steps {
                git branch: 'main', url: 'https://github.com/kwantke/lock_study.git'
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
                sh "docker build -t kwangko/test1:${imageTag} ."
            }
        }
        stage('Docker Push') {
            when {
                expression { params.DOCKER_PUSH == true }
            }
            agent any  // ✅ Docker Push 실행할 노드 지정
            steps {
                withDockerRegistry(credentialsId: 'dockerhub-signin', url: 'https://index.docker.io/v1/') {
                    sh "docker push kwangko/test1:${imageTag}"
                }
            }
        }
        stage('k8s push') {
            when {
              expression { params.K8S_PUSH == true }
            }
            agent any
            steps {
              sh 'git config --global user.email "test@naver.com"'
              sh 'git config --global user.name "kwantke"'
              sh 'git add -A'
              sh 'git commit -m "update! version: "' + imageTag + '" by Jenkins"'
              withCredentials({
                  gitUsernamePassword(
                      credentialsId: "github-signin",
                      usernameVariable: "GIT_USERNAME",
                      passwordVariable": "GIT_PASSWORD"
              )}) {
                  sh('sh push https://$GIT_USERNAME:$GIT_PASSWORD@github.com/kwantke/argocd.git')
              }
            }
        }
        stage('argocd sync') {
            when{
                expression { params.ARGOCD_SYNC == true}
            }
            agent any
            steps {
                sh """
                  curl -k -L \\
                    -X POST \\
                    -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \\
                    -H "Content-Type: application/json" \\
                    https://192.168.56.112:32695/api/v1/applications/argocd-hello-web/sync \\
                    -d '{
                      "revision": "HEAD",
                      "prune": false,
                      "dryRun": false,
                      "strategy": {
                        "hook": {
                          "force": false
                        }
                      },
                      "resources": []
                    }'
                """
            }
        }

    }
}
