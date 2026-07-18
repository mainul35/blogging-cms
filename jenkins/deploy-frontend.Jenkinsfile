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
        // See deploy-backend.Jenkinsfile for why this is needed: container_name:
        // in the compose file is fixed/host-wide-unique regardless of project,
        // so every job (and a human running docker compose by hand) must agree
        // on one COMPOSE_PROJECT_NAME or they'll fight over the same containers.
        COMPOSE_PROJECT_NAME = 'blogging-cms'
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
                        //
                        // The curl itself runs inside a --network host
                        // container via the docker CLI, not directly in this
                        // Jenkins container -- Jenkins is bridge-networked
                        // (vsd-auth-tunnel_default), so its own "localhost"
                        // is its own loopback, not the host's; a bare curl
                        // here fails to connect (exit 7) to Harbor on the
                        // host's :5000, silently, since -s suppresses the
                        // error and the pipe hides the exit code. Docker
                        // commands don't have this problem because they run
                        // against the host's own daemon via the mounted
                        // socket, which is what --network host reaches too.
                        def tagsRaw = sh(
                            script: '''
                                RESPONSE=$(docker run --rm --network host curlimages/curl -sf -u "$HARBOR_USER:$HARBOR_PASS" "http://${REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}/repositories/frontend/artifacts?with_tag=true&page_size=50")
                                CURL_EXIT=$?
                                echo "Harbor query: curl exit=$CURL_EXIT, response length=${#RESPONSE}" >&2
                                echo "$RESPONSE" | grep -oE '"name":"[^"]+"' | cut -d'"' -f4 | sort -ru
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
                withCredentials([
                    file(credentialsId: 'blogging-cms-prod-env', variable: 'ENV_FILE'),
                    usernamePassword(
                        credentialsId: 'harbor-robot-blogging-cms',
                        usernameVariable: 'HARBOR_USER',
                        passwordVariable: 'HARBOR_PASS'
                    )
                ]) {
                    sh '''
                        set -e
                        # See deploy-backend.Jenkinsfile's identical stage for why
                        # this uses `cat >` + explicit chmod instead of `cp`.
                        rm -f .env.deploy
                        cat "$ENV_FILE" > .env.deploy
                        chmod 600 .env.deploy
                        echo "IMAGE_TAG=${SELECTED_TAG}" >> .env.deploy
                        echo "REGISTRY=${REGISTRY}" >> .env.deploy
                        echo "HARBOR_PROJECT=${HARBOR_PROJECT}" >> .env.deploy

                        # The tag-picker stage only used these creds to query
                        # Harbor's REST API -- `docker compose pull` needs its
                        # own login against the host docker daemon, or it 401s
                        # with "no basic auth credentials".
                        echo "$HARBOR_PASS" | docker login ${REGISTRY} -u "$HARBOR_USER" --password-stdin
                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} pull frontend
                        docker compose --env-file .env.deploy -f ${COMPOSE_FILE} up -d --no-deps frontend
                        docker logout ${REGISTRY}

                        rm -f .env.deploy
                    '''
                }
            }
        }

        stage('Smoke test') {
            steps {
                sh '''
                    set -e
                    # A bare curl from inside this (bridge-networked) Jenkins
                    # container can't reach the host's published 127.0.0.1:2368
                    # -- same reason the tag-picker's curl needed --network
                    # host. Checking from inside the target container itself
                    # sidesteps host networking entirely.
                    #
                    # 127.0.0.1, not localhost: busybox wget resolves
                    # "localhost" to ::1 first, and Next's server only binds
                    # the IPv4 wildcard -- confirmed directly against the
                    # first deploy-frontend run (127.0.0.1 succeeds, localhost
                    # gets "Connection refused"). See docker-compose.prod.yml's
                    # frontend healthcheck for the same fix.
                    for i in $(seq 1 20); do
                      if docker exec blog_frontend wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1; then
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
