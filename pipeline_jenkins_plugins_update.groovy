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

// Splits the available updates into the ones to install and the ones to skip.
// An update that needs a newer Jenkins core, directly or through a dependency,
// would install and then fail to load after the restart, so it is skipped.
@NonCPS
Map<String, List<String>> pendingUpdates() {
    def updates = [install: [], skip: []]
    Jenkins.get().pluginManager.plugins.each { installed ->
        def update = installed.updateInfo
        if (update == null) {
            return
        }
        if (update.isForNewerHudson() || update.isNeededDependenciesForNewerJenkins()) {
            updates.skip << "${update.displayName} ${update.version} (needs Jenkins ${update.neededDependenciesRequiredCore})".toString()
        } else {
            updates.install << installed.shortName
        }
    }
    updates
}

// Refreshes the update site metadata now, like "Check now" in the UI.
// updateAllSites() skips every site fetched within the last day, so the job
// would compare against stale data. A rejected download (bad signature, invalid
// data) comes back as an error result instead of an exception, so check it.
@NonCPS
List<String> refreshUpdateSites() {
    Jenkins.get().updateCenter.sites
        .collect { it.updateDirectlyNow() }
        .findAll { it.kind == hudson.util.FormValidation.Kind.ERROR }
        .collect { it.message }
}

// deploy() completes its Future normally even when the download or install
// fails; the outcome is only in the job status, so check it explicitly.
@NonCPS
String installPlugin(String shortName) {
    def plugin = Jenkins.get().pluginManager.getPlugin(shortName).updateInfo
    def job = plugin.deploy(false).get(10, TimeUnit.MINUTES)
    if (job.status instanceof hudson.model.UpdateCenter.DownloadJob.Failure) {
        throw new hudson.AbortException("Installing ${plugin.displayName} ${plugin.version} failed: ${job.status.problem}")
    }
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
                    def siteErrors = refreshUpdateSites()
                    if (siteErrors) {
                        error "Update site refresh failed: ${siteErrors.join('; ')}"
                    }
                    def updates = pendingUpdates()
                    if (updates.skip) {
                        echo "Skipping updates that need a newer Jenkins: ${updates.skip.join(', ')}"
                    }
                    if (!updates.install) {
                        echo 'All plugins are up to date'
                        return
                    }
                    echo "Updating: ${updates.install.join(', ')}"
                    updates.install.each { name -> updatedPlugins << installPlugin(name) }
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
