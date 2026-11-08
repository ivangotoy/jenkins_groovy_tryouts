// Builds UPX, GoReleaser and Trivy from their default branches and installs
// them into /usr/local/bin on the build agent. UPX is built first because the
// Go binaries are compressed with it.

def checkoutTool(String name, String url, String branch) {
    dir(name) {
        checkout scmGit(branches: [[name: branch]],
                        extensions: [cloneOption(depth: 1, noTags: true, shallow: true)],
                        userRemoteConfigs: [[url: url]])
    }
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
        GOOS = 'linux'
        GOARCH = 'amd64'
        GOAMD64 = 'v3'
        GOFLAGS = '-trimpath -pgo=auto'
        LC_ALL = 'C'
    }

    stages {
        stage('Checkout') {
            parallel {
                stage('upx')        { steps { script { checkoutTool('upx', 'https://github.com/upx/upx.git', 'devel') } } }
                stage('goreleaser') { steps { script { checkoutTool('goreleaser', 'https://github.com/goreleaser/goreleaser.git', 'main') } } }
                stage('trivy')      { steps { script { checkoutTool('trivy', 'https://github.com/aquasecurity/trivy.git', 'main') } } }
            }
        }

        stage('Build UPX') {
            steps {
                dir('upx') {
                    sh '''
                        git submodule update --init --depth 1
                        make build/release -j"$(nproc)"
                        strip -s build/release/upx
                        build/release/upx -9 -q build/release/upx
                        sudo install -m 0755 build/release/upx /usr/local/bin/upx
                    '''
                }
            }
        }

        stage('Build Go tools') {
            parallel {
                stage('GoReleaser') {
                    steps {
                        dir('goreleaser') {
                            sh '''
                                go build -ldflags="-s -w" -o goreleaser .
                                upx -9 -q goreleaser
                                sudo install -m 0755 goreleaser /usr/local/bin/goreleaser
                            '''
                        }
                    }
                }
                stage('Trivy') {
                    steps {
                        dir('trivy') {
                            sh '''
                                go build -ldflags="-s -w" -o trivy ./cmd/trivy
                                upx -9 -q trivy
                                sudo install -m 0755 trivy /usr/local/bin/trivy
                            '''
                        }
                    }
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
