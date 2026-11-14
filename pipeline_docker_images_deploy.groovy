// Builds every image in the docker_images bake file, pushes it to the private
// registry, then recreates the long-running service containers on the agent.

// name: container name, image: repository under REGISTRY/home, args: extra `docker run` flags, cmd: command
def services = [
    [name: 'quake3e-baseq3',  image: 'quake3e',         args: '-p 4000:4000/udp', cmd: 'quake3'],
    [name: 'quake3e-cpma',    image: 'quake3e',         args: '-p 4003:4003/udp', cmd: 'q3cpma'],
    [name: 'v2raya',          image: 'v2raya',          args: '-p 2001-2040:2001-2040 --add-host=host.docker.internal:host-gateway', cmd: ''],
    [name: 'dnscrypt-proxy',  image: 'dnscrypt',        args: '-p 5353:5353/udp', cmd: ''],
    [name: 'openbullet2-web', image: 'openbullet2-web', args: '-p 5000:5000/tcp', cmd: ''],
]

pipeline {
    agent { label 'ryzen9' }

    options {
        timestamps()
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    environment {
        REGISTRY = 'digtvbg.com:6000'
        SOURCE_REPO = 'https://git.digtvbg.com/ivangotoy/docker_images.git'
        BUILDER = 'ci-amd64'
    }

    stages {
        stage('Checkout') {
            steps {
                withCredentials([string(credentialsId: 'gitea_jenkins_api_token', variable: 'GIT_TOKEN')]) {
                    // The auth header goes through GIT_CONFIG_* (git >= 2.31) instead of
                    // `git -c`, so the token never shows up in the process list.
                    sh '''
                        export GIT_CONFIG_COUNT=1
                        export GIT_CONFIG_KEY_0=http.extraHeader
                        export GIT_CONFIG_VALUE_0="Authorization: token ${GIT_TOKEN}"
                        git clone --quiet --depth 1 --branch main --single-branch "${SOURCE_REPO}" docker_images
                    '''
                }
            }
        }

        stage('Registry login') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker_registry_digtvbg_5000_credentials',
                                                  usernameVariable: 'REGISTRY_USER',
                                                  passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh 'echo "${REGISTRY_PASSWORD}" | docker login --username "${REGISTRY_USER}" --password-stdin "${REGISTRY}"'
                }
            }
        }

        stage('Build and push') {
            steps {
                dir('docker_images') {
                    sh '''
                        docker buildx inspect "${BUILDER}" >/dev/null 2>&1 || \
                            docker buildx create --name "${BUILDER}" --driver docker-container --platform linux/amd64 --bootstrap
                        docker buildx bake --builder "${BUILDER}" --pull --no-cache --push
                    '''
                }
            }
        }

        stage('Deploy services') {
            steps {
                script {
                    services.each { svc ->
                        sh """
                            docker pull ${env.REGISTRY}/home/${svc.image}
                            docker rm --force ${svc.name} 2>/dev/null || true
                            docker run --detach --restart unless-stopped --name ${svc.name} ${svc.args} ${env.REGISTRY}/home/${svc.image} ${svc.cmd}
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            sh '''
                docker logout "${REGISTRY}" || true
                docker buildx prune --builder "${BUILDER}" --force || true
                docker image prune --force || true
            '''
            cleanWs()
        }
    }
}
