pipeline {
    agent { label 'ALDERLAKE' }  // Specify the ALDERLAKE agent
    stages {
        stage('Temporarily Disable All Other Jobs') {
            steps {
                script {
                    println "Disabling all other jobs to simulate maintenance mode..."
                    // Disable all jobs except this one to prevent new builds from starting
                    Jenkins.instance.items.each { job ->
                        if (job instanceof hudson.model.Job && job.fullName != env.JOB_NAME) {
                            job.setDisabled(true)
                            println "Disabled job: ${job.fullName}"
                        }
                    }
                }
            }
        }
        stage('Wait for All Other Jobs to Complete') {
            steps {
                script {
                    println "Waiting for all other running jobs to complete..."
                    def currentBuildId = env.BUILD_ID

                    // Function to get all builds that are currently running, excluding this build
                    def getActiveJobs = {
                        Jenkins.instance.getAllItems(hudson.model.Job.class).collectMany { job ->
                            job.builds.findAll { build ->
                                build.isBuilding() && build.getId() != currentBuildId
                            }
                        }
                    }

                    // Initial check for active jobs
                    def activeJobs = getActiveJobs()
                    while (activeJobs.size() > 0) {
                        println "Currently running jobs: ${activeJobs.collect { it.displayName }}"
                        println "Waiting for currently running jobs to complete..."
                        sleep 10  // Check every 10 seconds
                        activeJobs = getActiveJobs()  // Refresh active jobs list
                    }
                    println "All other jobs have completed."
                }
            }
        }
        stage('Check for Plugin Updates') {
            steps {
                script {
                    println "Checking for plugin updates..."
                    Jenkins.instance.updateCenter.updateAllSites()
                    sleep 5  // Wait briefly for the update center to retrieve available updates
                }
            }
        }
        stage('Install Plugin Updates if Available') {
            steps {
                script {
                    def outdatedPlugins = Jenkins.instance.pluginManager.plugins.findAll { it.hasUpdate() }
                    if (outdatedPlugins) {
                        println "The following plugins have updates available:"
                        outdatedPlugins.each { plugin ->
                            println "${plugin.getDisplayName()} (${plugin.getShortName()}): Current version ${plugin.getVersion()}, New version ${plugin.getUpdateVersion()}"
                        }

                        // Download and install updates
                        println "Downloading and installing updates..."
                        outdatedPlugins.each { plugin ->
                            def update = Jenkins.instance.updateCenter.getPlugin(plugin.getShortName()).deploy(true)
                            update.get(10, java.util.concurrent.TimeUnit.MINUTES)  // Wait up to 10 minutes for each update
                            println "Updated ${plugin.getDisplayName()} to version ${plugin.getUpdateVersion()}"
                        }
                        println "All updates installed."
                    } else {
                        println "All plugins are up to date. No updates to install."
                    }
                }
            }
        }
        stage('Re-enable All Jobs') {
            steps {
                script {
                    println "Re-enabling all jobs after maintenance tasks..."
                    // Re-enable all jobs except this one to allow new builds to start
                    Jenkins.instance.items.each { job ->
                        if (job instanceof hudson.model.Job && job.fullName != env.JOB_NAME) {
                            job.setDisabled(false)
                            println "Re-enabled job: ${job.fullName}"
                        }
                    }
                }
            }
        }
        stage('Restart Jenkins if Updates Installed') {
            steps {
                script {
                    if (Jenkins.instance.pluginManager.plugins.any { it.hasUpdate() }) {
                        println "Restarting Jenkins to apply updates..."
                        Jenkins.instance.safeRestart()
                    } else {
                        println "Skipping restart as no updates were installed."
                    }
                }
            }
        }
    }
}

