// Deploys a specific, already-built backend image tag from Harbor.
// Building is a separate job (build-backend) -- this one never builds
// anything, only pulls and deploys what's already sitting in the registry.
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
        COMPOSE_FILE   = 'docker-compose.prod.yml'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Select image tag') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'harbor-robot-blogging-cms',
                    usernameVariable: 'HARBOR_USER',
                    passwordVariable: 'HARBOR_PASS'
                )]) {
                    script {
                        // Plain grep instead of a JSON-parsing step (readJSON
                        // needs the pipeline-utility-steps plugin, not
                        // installed) -- Harbor's artifact list always has
                        // `"name":"<tag>"` per tag, which is all we need here.
                        def tagsRaw = sh(
                            script: '''
                                curl -sf -u "$HARBOR_USER:$HARBOR_PASS" \
                                  "http://${REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}/repositories/backend/artifacts?with_tag=true&page_size=50" \
                                | grep -oP '"name":"\\K[^"]+' | sort -ru
                            ''',
                            returnStdout: true
                        ).trim()
                        def tagList = tagsRaw ? tagsRaw.readLines() : ['latest']
                        env.SELECTED_TAG = input(
                            message: 'Select the backend image tag to deploy',
                            parameters: [choice(
                                name: 'IMAGE_TAG',
                                choices: tagList.join('\n'),
                                description: 'Tags currently pushed to blogging-cms/backend in Harbor (newest first)'
                            )]
                        )
                    }
                }
            }
        }

        stage('Dependency health check') {
            steps {
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -e
                        docker compose --env-file "$ENV_FILE" -f ${COMPOSE_FILE} up -d postgres redis

                        wait_healthy() {
                          name=$1
                          for i in $(seq 1 30); do
                            status=$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo "starting")
                            if [ "$status" = "healthy" ]; then
                              echo "$name is healthy"
                              return 0
                            fi
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

        stage('Deploy selected image') {
            steps {
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        cp "$ENV_FILE" .env.deploy
                        echo "IMAGE_TAG=${SELECTED_TAG}" >> .env.deploy
                        echo "REGISTRY=${REGISTRY}" >> .env.deploy
                        echo "HARBOR_PROJECT=${HARBOR_PROJECT}" >> .env.deploy

                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} pull backend
                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} up -d --no-deps backend

                        rm -f .env.deploy
                    '''
                }
            }
        }

        stage('Smoke test') {
            steps {
                sh '''
                    set -e
                    # backend has no host-published port -- check from inside
                    # its own container instead of via a host curl.
                    for i in $(seq 1 20); do
                      if docker exec blog_backend wget -qO- http://localhost:8080/api/mail-settings/status >/dev/null 2>&1; then
                        echo "Backend (tag ${SELECTED_TAG}) is responding"
                        break
                      fi
                      [ "$i" = 20 ] && { echo "Backend never responded" >&2; exit 1; }
                      sleep 3
                    done
                '''
            }
        }
    }

    post {
        failure {
            echo 'Deploy failed -- see the failing stage above. To roll back, re-run this job and select the previous good tag.'
        }
    }
}
