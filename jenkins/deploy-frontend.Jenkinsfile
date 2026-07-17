// Deploys a specific, already-built frontend image tag from Harbor.
// Building is a separate job (build-frontend) -- this one never builds
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
                        // -E/cut instead of -P: PCRE's \K needs a UTF-8 locale
                        // that this Jenkins environment doesn't have ("grep -P
                        // supports only unibyte and UTF-8 locales"), which
                        // silently produced zero matches -- not an error the
                        // pipeline caught, just a wrong (but validly-typed)
                        // one-item choice list every time.
                        def tagsRaw = sh(
                            script: '''
                                curl -sf -u "$HARBOR_USER:$HARBOR_PASS" \
                                  "http://${REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}/repositories/frontend/artifacts?with_tag=true&page_size=50" \
                                | grep -oE '"name":"[^"]+"' | cut -d'"' -f4 | sort -ru
                            ''',
                            returnStdout: true
                        ).trim()
                        def tagList = tagsRaw ? tagsRaw.readLines() : ['latest']
                        env.SELECTED_TAG = input(
                            message: 'Select the frontend image tag to deploy',
                            parameters: [choice(
                                name: 'IMAGE_TAG',
                                choices: tagList.join('\n'),
                                description: 'Tags currently pushed to blogging-cms/frontend in Harbor (newest first)'
                            )]
                        )
                    }
                }
            }
        }

        stage('Dependency health check') {
            steps {
                // frontend depends_on backend being healthy (docker-compose.prod.yml);
                // backend itself depends on postgres/redis -- bring the whole
                // chain up so `up -d --no-deps frontend` below has something
                // healthy to point at even on a cold start.
                withCredentials([file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -e
                        docker compose --env-file "$ENV_FILE" -f ${COMPOSE_FILE} up -d postgres redis backend

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
                        wait_healthy blog_backend
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

                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} pull frontend
                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} up -d --no-deps frontend

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
                      if curl -sf http://127.0.0.1:2368/ >/dev/null; then
                        echo "Frontend (tag ${SELECTED_TAG}) is responding"
                        break
                      fi
                      [ "$i" = 20 ] && { echo "Frontend never responded" >&2; exit 1; }
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
