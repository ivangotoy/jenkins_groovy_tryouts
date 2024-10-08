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
        stage('Clean Workspace') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
        stage('Checkout Navidrome Repo') {
            steps {
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
        stage('Build and Deploy Navidrome') {
            steps {
                dir('navidrome') {
                    script {
                        sh '''
                          sed -i '15s/^/    goamd64:\\n/' .goreleaser.yml &&
                          sed -i '16s/^/      - v3\\n/' .goreleaser.yml &&
                          sed -i '15s/^/      - -trimpath\\n/' .goreleaser.yml &&
                          sed -i '16s/^/      - -pgo=auto\\n/' .goreleaser.yml &&
                          sed -i '9s/^/      - GOEXPERIMENT=newinliner\\n/' .goreleaser.yml &&
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
        stage('Clean Workspace After Build') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
    }
}

