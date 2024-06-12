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
				sh """
				git config --global http.postBuffer 157286400
				git clone --quiet --depth 1 -b main --single-branch https://${GITEA_API_KEY}@${GITEA_REPO}
				"""
			}
		}

		stage('BUILD DEPLOY X86_64 IMAGES') {
			steps {
				withCredentials([usernamePassword(credentialsId: credentialsID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
					sh "echo $PASSWORD | docker login -u $USERNAME --password-stdin $registryURL"
				}

				sh '''
				docker kill $(docker ps -q) || true
				sleep 7
				docker rm $(docker ps -a -f status=exited -q) || true
				docker system prune -af
				docker buildx rm amd64 || true
				docker buildx create --bootstrap --platform linux/amd64 --driver-opt image=moby/buildkit:nightly --name amd64 --use
				docker buildx prune -f
				docker pull --platform=linux/amd64 ubuntu:noble
				docker pull --platform=linux/amd64 digtvbg.com:5000/base
				'''
				dir('docker_images') {
					sh '''
					$buildcmd
					docker buildx prune -f
					docker pull ${registryURL}/dnscrypt
					docker pull ${registryURL}/openbullet2
					docker pull ${registryURL}/quake3e
					docker pull ${registryURL}/v2raya
					docker pull ${registryURL}/mitmproxy
					'''
					withCredentials([usernamePassword(credentialsId: credentialsID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
						sh "echo $PASSWORD | crane auth login $registryURL -u $USERNAME --password-stdin"
					}

					sh '''
					crane flatten ${registryURL}/dnscrypt
					crane flatten ${registryURL}/openbullet2
					crane flatten ${registryURL}/quake3e
					crane flatten ${registryURL}/v2raya
					crane flatten ${registryURL}/mitmproxy
					docker system prune -af
					docker pull digtvbg.com:5000/quake3e
					docker pull digtvbg.com:5000/v2raya
					docker pull digtvbg.com:5000/openbullet2
					docker pull digtvbg.com:5000/dnscrypt
					docker pull digtvbg.com:5000/mitmproxy
					docker run --restart=always -p 4000:4000/udp --name "Q3dedicated" -dit digtvbg.com:5000/quake3e quake3
					docker run --restart=always -p 4003:4003/udp --name "Q3CPMAded" -dit digtvbg.com:5000/quake3e q3cpma
					docker run --restart=always -p 2001-2040:2001-2040 --add-host=host.docker.internal:host-gateway -dit digtvbg.com:5000/v2raya
					docker run --restart=always -p 5353:5353/udp --name "DNSCRYPT-PROXY-V2" -dit digtvbg.com:5000/dnscrypt
					'''
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
