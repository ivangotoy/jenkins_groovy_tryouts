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
        def DATE = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy-HH-mm-ss"))
    }
    stages {
        stage('PHASE1: CLEAN AMD64') {
            steps {
                cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            }
        }
        stage('PHASE2: GIT CLONE FEISHIN') {
            steps {
                sh "git clone --quiet --depth 1 -b development --single-branch https://github.com/jeffvli/feishin.git"
            }
        }
        stage('PHASE3: BUILD AND DEPLOY FEISHIN') {
            steps {
                dir('feishin') {
                    script {
                        def Command1 = "sed -i 's/ --mac//g' package.json"
                        sh Command1
                        sh 'npm install --legacy-peer-deps'
                        sh 'npm run package:pr'
	  	        sh "ssh root@alder 'mkdir -p /var/www/html/files/feishin/feishin-$DATE'"
		        sh "rsync -v release/build/Feishin-*-linux-x64.tar.xz release/build/Feishin-*-win-x64.exe root@alder:/var/www/html/files/feishin/feishin-$DATE/"
		        sh 'ssh root@alder chown -R http: /var/www/html/files/feishin/'
		        sh "find /var/www/html/files/feishin -mindepth 1 -maxdepth 1 -type d -printf '%T+ %p\\n' | sort -r | awk '{if (NR > 5) print \$2}' | xargs -r rm -r"
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
