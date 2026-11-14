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
                    // Upload into a hidden staging directory and rename it only after the
                    // upload succeeded, so a failed run never leaves an empty or partial
                    // build that the retention below would count as one of the kept builds.
                    sh '''
                        name="feishin-$(date +%Y%m%d-%H%M%S)"
                        staging="${PUBLISH_DIR}/.partial-${name}"
                        ssh "${PUBLISH_HOST}" "rm -rf '${PUBLISH_DIR}'/.partial-feishin-* && mkdir -p '${staging}'"
                        rsync dist/Feishin-*-linux-setup.tar.xz dist/Feishin-*-win-setup.exe "${PUBLISH_HOST}:${staging}/"
                        ssh "${PUBLISH_HOST}" "mv '${staging}' '${PUBLISH_DIR}/${name}' && chown -R http: '${PUBLISH_DIR}' && \
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
