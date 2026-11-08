// Builds Navidrome from master as an x86-64-v3 binary and installs it on the router.

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
        PATH = "/usr/local/go/bin:${env.PATH}"
        GOAMD64 = 'v3'
        GOFLAGS = '-trimpath -pgo=auto'
        DEPLOY_TARGET = 'root@router:/mnt/sda1/Music/'
    }

    stages {
        stage('Checkout') {
            steps {
                dir('navidrome') {
                    shallowCheckout('https://github.com/navidrome/navidrome.git', 'master')
                }
            }
        }

        stage('Build') {
            steps {
                dir('navidrome') {
                    sh '''
                        make setup
                        make build
                        upx -9 -q navidrome
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                dir('navidrome') {
                    sh 'rsync -az --progress navidrome "${DEPLOY_TARGET}"'
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
