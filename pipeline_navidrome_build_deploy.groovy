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
                                  extensions: [[$class: 'CloneOption', depth: 1, shallow: true]],
                                  userRemoteConfigs: [[url: 'https://github.com/navidrome/navidrome.git']]
                        ])
                    }
                }
            }
        }
        
        stage('PHASE3: BUILD AND DEPLOY NAVIDROME') {
            steps {
                dir('navidrome') {
                    script {
                        sh '''
                        sed -i '14s/^/    goamd64:\\n      - v3\\n      - -trimpath\\n      - -pgo=auto\\n/' .goreleaser.yml &&
                        sed -i '8s/^/      - GOEXPERIMENT=newinliner\\n/' .goreleaser.yml &&
                        make setup &&
                        make buildjs &&
                        goreleaser build --clean --snapshot --single-target --id navidrome_linux_amd64 &&
                        upx -9 -q dist/navidrome_linux_amd64_linux_amd64_v3/navidrome &&
                        rsync -aPz4 dist/navidrome_linux_amd64_linux_amd64_v3/navidrome root@router:/mnt/sda1/Music/
                        '''
                    }
                }
            }
        }
        
        stage('PHASE4: CLEAN WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
    }
}

