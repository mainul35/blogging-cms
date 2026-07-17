// Builds the backend image and pushes it to Harbor. Deploying it is a
// separate job (deploy-backend) -- this one only ever produces a tagged,
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
        // "localhost" is deliberate -- this runs against the host's own
        // docker daemon (socket mounted into the Jenkins container), which
        // is where Harbor's nginx actually listens on :5000.
        REGISTRY       = 'localhost:5000'
        HARBOR_PROJECT = 'blogging-cms'
        IMAGE_TAG      = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
        IMAGE          = "${REGISTRY}/${HARBOR_PROJECT}/backend:${IMAGE_TAG}"
        IMAGE_LATEST   = "${REGISTRY}/${HARBOR_PROJECT}/backend:latest"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build image') {
            steps {
                sh "docker build -t ${IMAGE} -t ${IMAGE_LATEST} ./backend"
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
            echo "Pushed ${IMAGE} -- use tag '${IMAGE_TAG}' in deploy-backend to release it."
        }
        always {
            // Only prunes truly dangling/untagged layers -- never the image
            // this build (or any recent build) just pushed.
            sh 'docker image prune -f --filter "until=72h" || true'
        }
    }
}
