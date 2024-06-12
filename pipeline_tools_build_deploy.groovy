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
        LC_ALL = "C"
        LANG = "C"
        GOEXPERIMENT = "newinliner"
    }

    stages {
        stage('PHASE1: CLEAN WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
stage('PHASE2: GIT CLONE TOOLS') {
    steps {
        script {
            parallel(
                'goreleaser': {
                    dir('goreleaser') {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: 'main']],
                                  extensions: [[$class: 'CloneOption',
                                               depth: 1,
                                               noTags: true,
                                               reference: '',
                                               shallow: true]],
                                  userRemoteConfigs: [[url: 'https://github.com/goreleaser/goreleaser']]])
                    }
                },
                'upx': {
                    dir('upx') {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: 'devel']],
                                  extensions: [[$class: 'CloneOption',
                                               depth: 1,
                                               noTags: true,
                                               reference: '',
                                               shallow: true]],
                                  userRemoteConfigs: [[url: 'https://github.com/upx/upx']]])
                    }
                },
                'go-containerregistry': {
                    dir('go-containerregistry') {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: 'main']],
                                  extensions: [[$class: 'CloneOption',
                                               depth: 1,
                                               noTags: true,
                                               reference: '',
                                               shallow: true]],
                                  userRemoteConfigs: [[url: 'https://github.com/google/go-containerregistry']]])
                    }
                },
                'trivy': {
                    dir('trivy') {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: 'main']],
                                  extensions: [[$class: 'CloneOption',
                                               depth: 1,
                                               noTags: true,
                                               reference: '',
                                               shallow: true]],
                                  userRemoteConfigs: [[url: 'https://github.com/aquasecurity/trivy']]])
                    }
                }
            )
        }
    }
}


        stage('PHASE3: BUILD UPX') {
            steps {
                script {
                    try {
                        dir('upx') {
                            sh '''
                            echo "Environment Variables:"
                            echo "LC_ALL=$LC_ALL"
                            echo "LANG=$LANG"
                            echo "PATH=$PATH"
                            git submodule update --init
                            make build/release -j$(nproc)
                            strip -s build/release/upx
                            upx -9 -q build/release/upx
                            sudo cp build/release/upx /usr/local/bin
                            '''
                        }
                    } catch (Exception e) {
                        echo "Error during building UPX: ${e.message}"
                        currentBuild.result = 'FAILURE'
                        error("Stopping pipeline due to failed UPX build.")
                    }
                }
            }
        }

        stage('PHASE4: BUILD GORELEASER') {
            steps {
                script {
                    try {
                        dir('goreleaser') {
                            sh '''
                            echo "Environment Variables:"
                            echo "LC_ALL=$LC_ALL"
                            echo "LANG=$LANG"
                            echo "PATH=$PATH"
                            echo "GOOS=$GOOS"
                            echo "GOARCH=$GOARCH"
                            echo "GOAMD64=$GOAMD64"
                            echo "GOEXPERIMENT=$GOEXPERIMENT"
                            go mod tidy
                            go build -ldflags="-s -w" -trimpath -pgo=auto -o goreleaser .
                            upx -9 -q goreleaser
                            sudo cp goreleaser /usr/local/bin
                            '''
                        }
                    } catch (Exception e) {
                        echo "Error during building GoReleaser: ${e.message}"
                        currentBuild.result = 'FAILURE'
                        error("Stopping pipeline due to failed GoReleaser build.")
                    }
                }
            }
        }

        stage('PHASE5: BUILD & DEPLOY REMAINING TOOLS') {
            parallel {
                stage('Build Go Container Registry') {
                    steps {
                        script {
                            try {
                                dir('go-containerregistry') {
                                    sh '''
                                    echo "Environment Variables:"
                                    echo "LANG=$LANG"
                                    echo "PATH=$PATH"
                                    echo "GOOS=$GOOS"
                                    echo "GOARCH=$GOARCH"
                                    echo "GOAMD64=$GOAMD64"
                                    echo "GOEXPERIMENT=$GOEXPERIMENT"
                                    go mod tidy
                                    sed -i '93,100d' .goreleaser.yml
                                    sed -i '36s/^/  goamd64:\\n/' .goreleaser.yml
                                    sed -i '37s/^/    - v3\\n/' .goreleaser.yml
                                    sed -i '13s/^/  - GOEXPERIMENT=newinliner\\n/' .goreleaser.yml
                                    sed -i '17s/^/  - -pgo=auto\\n/' .goreleaser.yml
                                    goreleaser build --clean --snapshot --single-target --id crane
                                    upx -9 -q dist/crane_linux_amd64_v3/crane
                                    sudo cp dist/crane_linux_amd64_v3/crane /usr/local/bin
                                    '''
                                }
                            } catch (Exception e) {
                                echo "Error during building Go Container Registry: ${e.message}"
                                currentBuild.result = 'FAILURE'
                                error("Stopping pipeline due to failed Go Container Registry build.")
                            }
                        }
                    }
                }

                stage('Build Trivy') {
                    steps {
                        script {
                            try {
                                dir('trivy') {
                                    sh '''
                                    echo "Environment Variables:"
                                    echo "LANG=$LANG"
                                    echo "PATH=$PATH"
                                    echo "GOOS=$GOOS"
                                    echo "GOARCH=$GOARCH"
                                    echo "GOAMD64=$GOAMD64"
                                    echo "GOEXPERIMENT=$GOEXPERIMENT"
                                    go mod tidy
                                    sed -i '14s/^/    goamd64:\\n/' goreleaser.yml
                                    sed -i '15s/^/      - v3\\n/' goreleaser.yml
                                    sed -i '6s/^/    flags:\\n/' goreleaser.yml
                                    sed -i '7s/^/      - -trimpath\\n/' goreleaser.yml
                                    sed -i '8s/^/      - -pgo=auto\\n/' goreleaser.yml
                                    goreleaser build --clean --snapshot --single-target --id build-linux
                                    upx -9 -q dist/build-linux_linux_amd64_v3/trivy
                                    sudo cp dist/build-linux_linux_amd64_v3/trivy /usr/local/bin
                                    '''
                                }
                            } catch (Exception e) {
                                echo "Error during building Trivy: ${e.message}"
                                currentBuild.result = 'FAILURE'
                                error("Stopping pipeline due to failed Trivy build.")
                            }
                        }
                    }
                }
            }
        }

        stage('PHASE6: POST BUILD CLEAN WORKSPACE') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
    }
}
