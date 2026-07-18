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
        // Without this, docker compose derives the project name from the
        // workspace directory -- which differs per Jenkins job (each gets
        // its own workspace folder name). container_name: in the compose
        // file is a fixed, host-wide-unique name regardless of project, so
        // two different project namespaces both trying to manage
        // "blog_postgres" collide. Pinning one shared name is what lets
        // every job (this one, deploy-frontend, and a human running
        // docker compose by hand) recognize and reuse the same stack
        // instead of fighting over the same container names.
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
                        // Plain grep instead of a JSON-parsing step (readJSON
                        // needs the pipeline-utility-steps plugin, not
                        // installed) -- Harbor's artifact list always has
                        // `"name":"<tag>"` per tag, which is all we need here.
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
                                RESPONSE=$(docker run --rm --network host curlimages/curl -sf -u "$HARBOR_USER:$HARBOR_PASS" "http://${REGISTRY}/api/v2.0/projects/${HARBOR_PROJECT}/repositories/backend/artifacts?with_tag=true&page_size=50")
                                CURL_EXIT=$?
                                echo "Harbor query: curl exit=$CURL_EXIT, response length=${#RESPONSE}" >&2
                                echo "$RESPONSE" | grep -oE '"name":"[^"]+"' | cut -d'"' -f4 | sort -ru
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
                        set -e
                        # `cp` preserves the source file's permission bits -- and
                        # withCredentials' secret-file copy is chmod 0400 (read-only,
                        # even for its own owner), which `cp` would carry straight
                        # onto .env.deploy. That silently broke a later run: the
                        # subsequent `>>` appends failed, and because this stage had
                        # no `set -e` back then, execution carried on anyway and the
                        # trailing `rm -f` never got a chance to run (build aborted
                        # first) -- leaving a stale 0400 file that blocked every
                        # later build at this exact line. `cat >` instead of `cp`
                        # gets a fresh, normally-permissioned file from the shell
                        # itself; the leading rm -f and explicit chmod are belt and
                        # suspenders against the same failure mode recurring.
                        rm -f .env.deploy
                        cat "$ENV_FILE" > .env.deploy
                        chmod 600 .env.deploy
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
