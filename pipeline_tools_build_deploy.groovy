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
    }
    stages {
        stage('PHASE1: CLEAN WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
        stage('PHASE2: GIT CLONE TOOLS') {
            steps {
                sh 'printf "https://github.com/goreleaser/goreleaser\nhttps://github.com/upx/upx.git\nhttps://github.com/google/go-containerregistry\nhttps://github.com/aquasecurity/trivy.git" | xargs -I{} -P4 git clone --quiet --depth 1 --single-branch {}'
            }
        }
        stage('PHASE3: BUILD & DEPLOY TOOLS') {
            steps {
                dir('upx') {
                    sh 'git submodule update --init'
                    sh 'make build/release -j20'
                    sh 'strip -s build/release/upx'
                    sh 'upx -9 -q build/release/upx'
                    sh 'sudo cp build/release/upx /usr/local/bin'
                }
                dir('goreleaser') {
                    sh 'go mod tidy'
                    sh 'go build -ldflags="-s -w" -trimpath -o goreleaser .'
                    sh 'upx -9 -q goreleaser'
                    sh 'sudo cp goreleaser /usr/local/bin'
                }
                dir('go-containerregistry') {
                    script {
                        def Command1 = """sed -i '93,100d' .goreleaser.yml"""
                        def Command2 = """sed -i '36s/^/  goamd64:\\n/' .goreleaser.yml"""
                        def Command3 = """sed -i '37s/^/    - v3\\n/' .goreleaser.yml"""
                        sh 'go mod tidy'
                        sh Command1
                        sh Command2
                        sh Command3
                        sh 'goreleaser build --clean --snapshot --single-target --id crane'
                        sh 'upx -9 -q dist/crane_linux_amd64_v3/crane'
                        sh 'sudo cp dist/crane_linux_amd64_v3/crane /usr/local/bin'
                    }
                }
                dir ('trivy') {
                    script {
                        def Command1 = """sed -i '14s/^/    goamd64:\\n/' goreleaser.yml"""
                        def Command2 = """sed -i '15s/^/      - v3\\n/' goreleaser.yml"""
                        sh 'go mod tidy'
                        sh Command1
                        sh Command2
                        sh 'goreleaser build --clean --snapshot --single-target --id build-linux'
                        sh 'upx -9 -q dist/build-linux_linux_amd64_v3/trivy'
                        sh 'sudo cp dist/build-linux_linux_amd64_v3/trivy /usr/local/bin'
                    }
                }
            }
        }
        stage('PHASE4: POST BUILD CLEAN WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
    }
}
