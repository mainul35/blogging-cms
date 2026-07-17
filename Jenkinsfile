// Deploys blogging-cms-app to proxy-vm as Docker containers.
//
// Topology this assumes (see docs/deployment/DEPLOYMENT.md for the full
// picture): Jenkins itself runs as a container ON proxy-vm, so every stage
// below runs docker/docker-compose directly against the host it's already
// on -- no SSH hop needed mid-pipeline. That means the Jenkins container
// needs the host's docker socket mounted in (and the docker CLI installed
// in the Jenkins image) so these steps can actually reach the Docker
// daemon; that's a one-time Jenkins container setting, not something this
// pipeline can do for itself.
//
// Required Jenkins credentials (Manage Jenkins > Credentials):
//   harbor-robot-blogging-cms   (username/password) - Harbor robot account,
//                                 see docs/deployment/harbor-registry-setup.md
//   blogging-cms-prod-env       (secret file) - the .env docker-compose.prod.yml
//                                 reads on the deploy host; see DEPLOYMENT.md
//                                 for the full variable list
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // Must include Harbor's https port -- it listens on 8443, not 443,
        // to stay clear of the app's own edge proxy on 80/443. See
        // docs/deployment/harbor-registry-setup.md.
        REGISTRY        = 'harbor.proxy-vm.local:8443'
        HARBOR_PROJECT  = 'blogging-cms'
        COMPOSE_FILE    = 'docker-compose.prod.yml'
        IMAGE_TAG       = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
        BACKEND_IMAGE   = "${REGISTRY}/${HARBOR_PROJECT}/backend:${IMAGE_TAG}"
        FRONTEND_IMAGE  = "${REGISTRY}/${HARBOR_PROJECT}/frontend:${IMAGE_TAG}"
        BACKEND_LATEST  = "${REGISTRY}/${HARBOR_PROJECT}/backend:latest"
        FRONTEND_LATEST = "${REGISTRY}/${HARBOR_PROJECT}/frontend:latest"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Dependency health check') {
            steps {
                // postgres/redis are the app's only hard dependencies (see
                // docker-compose.prod.yml); `docker compose up -d` on
                // services that are already running and healthy is a no-op,
                // so this is safe to run on every build, not just cold starts.
                // Needs the same env file as later stages: docker-compose.prod.yml's
                // DB_PASSWORD etc. use a `${VAR:?...}` hard-fail guard, which
                // trips even for `up -d postgres redis` alone if nothing supplies it.
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -e
                        echo "Ensuring postgres + redis are up..."
                        docker compose --env-file "$ENV_FILE" -f ${COMPOSE_FILE} up -d postgres redis

                        wait_healthy() {
                          name=$1
                          for i in $(seq 1 30); do
                            status=$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo "starting")
                            if [ "$status" = "healthy" ]; then
                              echo "$name is healthy"
                              return 0
                            fi
                            echo "$name is $status, waiting..."
                            sleep 2
                          done
                          echo "$name never became healthy" >&2
                          docker logs --tail 50 "$name" || true
                          exit 1
                        }
                        wait_healthy blog_postgres
                        wait_healthy blog_redis
                    '''
                }
            }
        }

        stage('Build backend image') {
            steps {
                sh """
                    docker build -t ${BACKEND_IMAGE} -t ${BACKEND_LATEST} ./backend
                """
            }
        }

        stage('Build frontend image') {
            steps {
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -a
                        . "$ENV_FILE"
                        set +a
                        docker build \
                          --build-arg BACKEND_URL=http://backend:8080 \
                          --build-arg NEXT_PUBLIC_BACKEND_URL=https://${PUBLIC_DOMAIN} \
                          -t ${FRONTEND_IMAGE} -t ${FRONTEND_LATEST} \
                          ./frontend
                    '''
                }
            }
        }

        stage('Push images to Harbor') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-robot-blogging-cms',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    sh '''
                        echo "$HARBOR_PASS" | docker login ${REGISTRY} -u "$HARBOR_USER" --password-stdin
                        docker push ${BACKEND_IMAGE}
                        docker push ${BACKEND_LATEST}
                        docker push ${FRONTEND_IMAGE}
                        docker push ${FRONTEND_LATEST}
                        docker logout ${REGISTRY}
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        cp "$ENV_FILE" .env.deploy
                        echo "IMAGE_TAG=${IMAGE_TAG}" >> .env.deploy
                        echo "REGISTRY=${REGISTRY}" >> .env.deploy
                        echo "HARBOR_PROJECT=${HARBOR_PROJECT}" >> .env.deploy

                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} pull backend frontend edge
                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} up -d --no-deps backend frontend edge

                        rm -f .env.deploy
                    '''
                }
            }
        }

        stage('Smoke test') {
            steps {
                sh '''
                    set -e
                    for i in $(seq 1 20); do
                      if curl -sf http://localhost/api/mail-settings/status >/dev/null; then
                        echo "Backend (via edge) is responding"
                        break
                      fi
                      [ "$i" = 20 ] && { echo "Backend never responded through edge" >&2; exit 1; }
                      sleep 3
                    done
                    curl -sf http://localhost/ >/dev/null || (echo "Frontend (via edge) did not respond" >&2; exit 1)
                '''
            }
        }
    }

    post {
        failure {
            echo 'Deploy failed -- see the failing stage above. Rollback steps: docs/deployment/DEPLOYMENT.md#rollback'
        }
        always {
            // Keeps the last few tagged images around for rollback; only
            // prunes truly dangling/untagged layers, never the images this
            // build or recent ones just pushed.
            sh 'docker image prune -f --filter "until=72h" || true'
        }
    }
}
