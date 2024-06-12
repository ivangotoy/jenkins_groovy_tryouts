pipeline {
    agent {
        label 'ryzen9'
    }
    options {
        timestamps()
        skipDefaultCheckout(true)
    }
    environment {
        PATH = "/usr/local/go/bin:${env.PATH}"
    }
    stages {
        stage('PHASE1: CLEAN AMD64') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
stage('PHASE2: GIT CLONE NAVIDROME') {
    steps {
        script {
            dir('navidrome') {
                checkout([$class: 'GitSCM',
                          branches: [[name: 'master']],
                          extensions: [[$class: 'CloneOption',
                                       depth: 1,
                                       noTags: true,
                                       reference: '',
                                       shallow: true]],
                          userRemoteConfigs: [[url: 'https://github.com/navidrome/navidrome.git']]])
            }
        }
    }
}
        stage('PHASE3: BUILD AND DEPLOY NAVIDROME') {
            steps {
                dir('navidrome') {
                    script {
                        sh '''
                          sed -i '14s/^/    goamd64:\\n/' .goreleaser.yml &&
                          sed -i '15s/^/      - v3\\n/' .goreleaser.yml &&
                          sed -i '14s/^/      - -trimpath\\n/' .goreleaser.yml &&
                          sed -i '15s/^/      - -pgo=auto\\n/' .goreleaser.yml &&
                          sed -i '8s/^/      - GOEXPERIMENT=newinliner\\n/' .goreleaser.yml &&
                          make setup &&
                          make buildjs &&
                          goreleaser build --clean --snapshot --single-target --id navidrome_linux_amd64 &&
                          upx -9 -q dist/navidrome_linux_amd64_linux_amd64_v3/navidrome &&
                          rsync -aPz4 dist/navidrome_linux_amd64_linux_amd64_v3/navidrome root@router:/mnt/sda1/Music &&
                          ssh root@router '/mnt/sda1/NAVI-UPDATE'
                        '''
                    }
                }
            }
        }
        stage('PHASE4: LAST Clean WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
    }
}

