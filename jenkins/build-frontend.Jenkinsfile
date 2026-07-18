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
                        set -e
                        # Only PUBLIC_DOMAIN is actually needed here -- sourcing
                        # the whole file (`set -a; . "$ENV_FILE"`) used to export
                        # every var in it, and Jenkins' sh step traces each line
                        # it executes (the "+ ..." prefixes), which dumped every
                        # secret in the prod .env -- DB_PASSWORD, JWT_SECRET,
                        # ADMIN_RESET_SECRET, INTERNAL_AUTH_SECRET, NEXTAUTH_SECRET
                        # -- in plaintext into the console log on every build.
                        # withCredentials only masks the literal $ENV_FILE path/
                        # blob, not values later split out of it, so none of that
                        # got redacted. Extracting just the one line we need
                        # never puts anything else in the trace at all.
                        PUBLIC_DOMAIN=$(grep '^PUBLIC_DOMAIN=' "$ENV_FILE" | cut -d= -f2-)
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
