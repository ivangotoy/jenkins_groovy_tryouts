// -- README:
// -- RTFM:
// -- https://digtvbg.com/files/MEDIA/DOCKER-INFO.txt
pipeline {
	agent {
		node {
			label 'ryzen9'
		}
	}

	options {
		timestamps()
		skipDefaultCheckout(true)
	}

	environment {
		GITEA_API_KEY = credentials('gitea_jenkins_api_token')
		GITEA_REPO = 'gitea.com/ivangotoy/docker_images.git'
		registryURL = "digtvbg.com:5000"
		credentialsID = "docker_registry_digtvbg_5000_credentials"
		buildcmd = "docker buildx bake --push --no-cache"

	}

	stages {
		stage('CLEAN WORKSPACE') {
			steps {
				cleanWs(deleteDirs: true, disableDeferredWipeout: true)
			}
		}

		stage('CLONE FROM GITEA') {
			steps {
				sh "git config --global http.postBuffer 157286400"
				sh "git clone --quiet --depth 1 -b main --single-branch https://${GITEA_API_KEY}@${GITEA_REPO}"
			}
		}

		stage('BUILD DEPLOY MULTIARCH IMAGES') {
			steps {
				withCredentials([usernamePassword(credentialsId: credentialsID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
					sh "echo $PASSWORD | docker login -u $USERNAME --password-stdin $registryURL"
				}

				sh 'docker kill $(docker ps -q) || true'
				sh 'sleep 7'
				sh 'docker rm $(docker ps -a -f status=exited -q) || true'
				sh 'docker system prune -af'
				sh 'docker buildx rm amd64 || true'
				sh 'docker buildx create --bootstrap --platform linux/amd64 --driver-opt image=moby/buildkit:nightly --name amd64 --use'
				sh 'docker buildx prune -f'
				sh 'docker pull --platform=linux/amd64 ubuntu:noble'
				sh 'docker pull --platform=linux/amd64 digtvbg.com:5000/base'
				dir('docker_images') {
					sh '$buildcmd'
					sh 'docker buildx prune -f'
					sh 'docker pull ${registryURL}/dnscrypt'
					sh 'docker pull ${registryURL}/openbullet2'
					sh 'docker pull ${registryURL}/quake3e'
					sh 'docker pull ${registryURL}/v2raya'
					sh 'docker pull ${registryURL}/mitmproxy'
					withCredentials([usernamePassword(credentialsId: credentialsID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
						sh "echo $PASSWORD | crane auth login $registryURL -u $USERNAME --password-stdin"
					}

					sh 'crane flatten ${registryURL}/dnscrypt'
					sh 'crane flatten ${registryURL}/openbullet2'
					sh 'crane flatten ${registryURL}/quake3e'
					sh 'crane flatten ${registryURL}/v2raya'
					sh 'crane flatten ${registryURL}/mitmproxy'
					sh 'docker system prune -af'
					sh 'docker pull digtvbg.com:5000/quake3e'
					sh 'docker pull digtvbg.com:5000/v2raya'
					sh 'docker pull digtvbg.com:5000/openbullet2'
					sh 'docker pull digtvbg.com:5000/dnscrypt'
					sh 'docker pull digtvbg.com:5000/mitmproxy'
					sh 'docker run --restart=always -p 4000:4000/udp --name "Q3dedicated" -dit digtvbg.com:5000/quake3e quake3'
					sh 'docker run --restart=always -p 4003:4003/udp --name "Q3CPMAded" -dit digtvbg.com:5000/quake3e q3cpma'
					sh 'docker run --restart=always -p 2001-2040:2001-2040 --add-host=host.docker.internal:host-gateway -dit digtvbg.com:5000/v2raya'
					sh 'docker run --restart=always -p 5353:5353/udp --name "DNSCRYPT-PROXY-V2" -dit digtvbg.com:5000/dnscrypt'
				}
			}
		}

		stage('POST BUILD CLEAN WORKSPACE') {
			steps {
				cleanWs(deleteDirs: true, disableDeferredWipeout: true)
			}
		}
	}
}
