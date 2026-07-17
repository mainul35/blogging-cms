// Builds the frontend image and pushes it to Harbor. Deploying it is a
// separate job (deploy-frontend) -- this one only ever produces a tagged,
// pushed image; nothing here touches the running app.
//
// Part of the blog.mainul35.dev folder: build-backend, build-frontend,
// deploy-backend, deploy-frontend. See docs/deployment/DEPLOYMENT.md.
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        REGISTRY       = 'localhost:5000'
        HARBOR_PROJECT = 'blogging-cms'
        IMAGE_TAG      = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
        IMAGE          = "${REGISTRY}/${HARBOR_PROJECT}/frontend:${IMAGE_TAG}"
        IMAGE_LATEST   = "${REGISTRY}/${HARBOR_PROJECT}/frontend:latest"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build image') {
            steps {
                // NEXT_PUBLIC_BACKEND_URL is resolved into the client JS bundle
                // at build time (Next.js can't pick this up at container
                // runtime), so it has to come from the same prod .env every
                // deploy uses -- PUBLIC_DOMAIN must already be the real
                // public origin this app is reached on.
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -a
                        . "$ENV_FILE"
                        set +a
                        docker build \
                          --build-arg BACKEND_URL=http://backend:8080 \
                          --build-arg NEXT_PUBLIC_BACKEND_URL=https://${PUBLIC_DOMAIN} \
                          -t ${IMAGE} -t ${IMAGE_LATEST} \
                          ./frontend
                    '''
                }
            }
        }

        stage('Push to Harbor') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-robot-blogging-cms',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    sh '''
                        echo "$HARBOR_PASS" | docker login ${REGISTRY} -u "$HARBOR_USER" --password-stdin
                        docker push ${IMAGE}
                        docker push ${IMAGE_LATEST}
                        docker logout ${REGISTRY}
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Pushed ${IMAGE} -- use tag '${IMAGE_TAG}' in deploy-frontend to release it."
        }
        always {
            sh 'docker image prune -f --filter "until=72h" || true'
        }
    }
}
