import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

pipeline {
    agent {
        label 'ryzen9'
    }
    options {
        timestamps()
        skipDefaultCheckout(true)
    }
    environment {
        DATE = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss"))
    }
    stages {
        stage('PHASE1: CLEAN AMD64') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
        stage('PHASE2: GIT CLONE FEISHIN') {
            steps {
                script {
                    dir('feishin') {
                        checkout([$class: 'GitSCM',
                                branches: [[name: 'development']],
                                extensions: [[$class: 'CloneOption',
                                            depth: 1,
                                            noTags: true,
                                            reference: '',
                                            shallow: true]],
                                userRemoteConfigs: [[url: 'https://github.com/jeffvli/feishin.git']]])
                    }
                }
            }
        }
        stage('PHASE3: BUILD AND DEPLOY FEISHIN') {
            steps {
                script {
                    dir('feishin') {
                        sh '''
                            sed -i 's/ --mac//g' package.json
                            npm ci --legacy-peer-deps
                            npm run package:pr
                            ssh root@alder "mkdir -p /var/www/html/files/feishin/feishin-${DATE}"
                            rsync release/build/Feishin-*-linux-x64.tar.xz release/build/Feishin-*-win-x64.exe root@alder:/var/www/html/files/feishin/feishin-${DATE}/
                            ssh root@alder 'chown -R http: /var/www/html/files/feishin/ && bash /tmp/cleanup_builds.sh'
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

