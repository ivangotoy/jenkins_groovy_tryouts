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
    router = 'root@192.168.1.1'
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
            def sedCommand1 = """sed -i '14s/^/    goamd64:\\n/' .goreleaser.yml"""
            def sedCommand2 = """sed -i '15s/^/      - v3\\n/' .goreleaser.yml"""
            sh 'make setup'
            sh 'make buildjs'
            sh sedCommand1
            sh sedCommand2
            sh 'goreleaser build --clean --snapshot --single-target --id navidrome_linux_amd64'
            sh 'upx -9 -q dist/navidrome_linux_amd64_linux_amd64_v3/navidrome'
            sh 'rsync -avPz dist/navidrome_linux_amd64_linux_amd64_v3/navidrome root@router:/mnt/sda1/Music'
            sh "ssh root@router '/mnt/sda1/navi-update'"
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
