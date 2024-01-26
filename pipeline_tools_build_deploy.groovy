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
					sh 'go mod tidy'
					sh "sed -i '93,100d' .goreleaser.yml"
					sh 'goreleaser build --clean --snapshot --single-target --id crane'
					sh 'upx -9 -q dist/crane_linux_amd64_v1/crane'
					sh 'sudo cp dist/crane_linux_amd64_v1/crane /usr/local/bin'
				}

				dir ('trivy') {
				sh 'go mod tidy'
				sh 'goreleaser build --clean --snapshot --single-target --id build-linux'
				sh 'upx -9 -q dist/build-linux_linux_amd64_v1/trivy'
				sh 'sudo cp dist/build-linux_linux_amd64_v1/trivy /usr/local/bin'
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
