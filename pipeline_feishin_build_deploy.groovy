// Builds Feishin (development branch) for Linux and Windows and publishes the
// installers to the alder web server, keeping the five most recent builds.

def shallowCheckout(String url, String branch) {
    checkout scmGit(branches: [[name: branch]],
                    extensions: [cloneOption(depth: 1, noTags: true, shallow: true)],
                    userRemoteConfigs: [[url: url]])
}

pipeline {
    agent { label 'ryzen9' }

    options {
        timestamps()
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        PUBLISH_HOST = 'root@alder'
        PUBLISH_DIR = '/var/www/html/files/feishin'
        KEEP_BUILDS = '5'
    }

    stages {
        stage('Checkout') {
            steps {
                dir('feishin') {
                    shallowCheckout('https://github.com/jeffvli/feishin.git', 'development')
                }
            }
        }

        stage('Build') {
            steps {
                dir('feishin') {
                    sh '''
                        pnpm install --frozen-lockfile
                        pnpm run build
                        pnpm exec electron-builder --linux --win --publish never
                    '''
                }
            }
        }

        stage('Publish') {
            steps {
                dir('feishin') {
                    sh '''
                        target="${PUBLISH_DIR}/feishin-$(date +%Y%m%d-%H%M%S)"
                        ssh "${PUBLISH_HOST}" "mkdir -p '${target}'"
                        rsync dist/Feishin-*-linux-setup.tar.xz dist/Feishin-*-win-setup.exe "${PUBLISH_HOST}:${target}/"
                        ssh "${PUBLISH_HOST}" "chown -R http: '${PUBLISH_DIR}' && \
                            ls -1dt '${PUBLISH_DIR}'/feishin-*/ | tail -n +$((KEEP_BUILDS + 1)) | xargs -r rm -rf"
                    '''
                }
            }
        }
    }

    post {
        always {
            cleanWs(deleteDirs: true, disableDeferredWipeout: true)
        }
    }
}
