// Maintenance job: pauses all other jobs, waits for running builds to finish,
// installs available plugin updates and restarts Jenkins if anything changed.
// Uses the Jenkins API directly, so it must run as a trusted (unsandboxed) job.

import java.util.concurrent.TimeUnit
import jenkins.model.Jenkins
import jenkins.model.ParameterizedJobMixIn.ParameterizedJob

@NonCPS
List<String> disableOtherJobs(String self) {
    Jenkins.get().getAllItems(ParameterizedJob)
        .findAll { it.fullName != self && !it.disabled }
        .collect { job ->
            job.disabled = true
            job.fullName
        }
}

@NonCPS
void enableJobs(List<String> names) {
    names.each { name -> Jenkins.get().getItemByFullName(name, ParameterizedJob)?.setDisabled(false) }
}

@NonCPS
List<String> otherRunningBuilds(String self) {
    Jenkins.get().getAllItems(hudson.model.Job)
        .collectMany { job -> job.builds.findAll { it.building } }
        .findAll { it.externalizableId != self }
        .collect { it.fullDisplayName }
}

@NonCPS
List<String> outdatedPlugins() {
    Jenkins.get().pluginManager.plugins.findAll { it.hasUpdate() }.collect { it.shortName }
}

@NonCPS
String installPlugin(String shortName) {
    def plugin = Jenkins.get().updateCenter.getPlugin(shortName)
    plugin.deploy(false).get(10, TimeUnit.MINUTES)
    "${plugin.displayName} -> ${plugin.version}"
}

def pausedJobs = []
def updatedPlugins = []

pipeline {
    agent { label 'ALDERLAKE' }

    options {
        timestamps()
        skipDefaultCheckout()
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    stages {
        stage('Pause other jobs') {
            steps {
                script {
                    pausedJobs = disableOtherJobs(env.JOB_NAME)
                    echo "Paused ${pausedJobs.size()} job(s)"
                }
            }
        }

        stage('Wait for running builds') {
            steps {
                script {
                    def self = currentBuild.rawBuild.externalizableId
                    waitUntil(initialRecurrencePeriod: 10000, quiet: true) {
                        def running = otherRunningBuilds(self)
                        if (running) {
                            echo "Still running: ${running.join(', ')}"
                        }
                        running.isEmpty()
                    }
                }
            }
        }

        stage('Update plugins') {
            steps {
                script {
                    Jenkins.get().updateCenter.updateAllSites()
                    def outdated = outdatedPlugins()
                    if (!outdated) {
                        echo 'All plugins are up to date'
                        return
                    }
                    echo "Updating: ${outdated.join(', ')}"
                    outdated.each { name -> updatedPlugins << installPlugin(name) }
                    echo "Installed:\n${updatedPlugins.join('\n')}"
                }
            }
        }
    }

    post {
        always {
            script {
                enableJobs(pausedJobs)
                echo "Resumed ${pausedJobs.size()} job(s)"
            }
        }
        success {
            script {
                if (updatedPlugins) {
                    echo 'Scheduling a safe restart to load the updated plugins'
                    Jenkins.get().safeRestart()
                }
            }
        }
    }
}
