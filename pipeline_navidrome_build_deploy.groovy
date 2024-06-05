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
        GOOS = "linux"
        GOARCH = "amd64"
        GOAMD64 = "v3"
        router = 'root@router'
        CREDENTIALS_ID = 'ryzen9'
    }
    stages {
        stage('PHASE1: CLEAN AMD64') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
        stage('PHASE2: GIT CLONE NAVIDROME') {
            steps {
                sh "git clone --quiet --depth 1 -b master --single-branch https://github.com/navidrome/navidrome.git"
            }
        }
        stage('PHASE3: BUILD AND DEPLOY NAVIDROME') {
            steps {
                dir('navidrome') {
                    script {
                        def Command1 = """sed -i '14s/^/    goamd64:\\n/' .goreleaser.yml"""
                        def Command2 = """sed -i '15s/^/      - v3\\n/' .goreleaser.yml"""
                        def Command3 = """sed -i '14s/^/      - -trimpath\\n/' .goreleaser.yml"""
                        def Command4 = """sed -i '15s/^/      - -pgo=auto\\n/' .goreleaser.yml"""
                        def Command5 = """sed -i '8s/^/      - GOEXPERIMENT=newinliner\\n/' .goreleaser.yml"""
                        sh 'make setup'
                        sh 'make buildjs'
                        sh Command1
                        sh Command2
                        sh Command3
                        sh Command4
                        sh Command5
                        sh 'goreleaser build --clean --snapshot --single-target --id navidrome_linux_amd64'
                        sh 'upx -9 -q dist/navidrome_linux_amd64_linux_amd64_v3/navidrome'
                        sh 'rsync -aPz4 dist/navidrome_linux_amd64_linux_amd64_v3/navidrome root@router:/mnt/sda1/Music'
                        sh "ssh root@router '/mnt/sda1/NAVI-UPDATE'"
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
