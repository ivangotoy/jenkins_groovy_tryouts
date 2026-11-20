> [!NOTE]
> 🏡 **Home:** [git.digtvbg.com/ivangotoy/jenkins_groovy_tryouts](https://git.digtvbg.com/ivangotoy/jenkins_groovy_tryouts), where code, issues and releases live.
> 🪞 **Mirror:** [github.com/ivangotoy/jenkins_groovy_tryouts](https://github.com/ivangotoy/jenkins_groovy_tryouts), read-only, synced automatically.

# jenkins_groovy_tryouts

Declarative Jenkins pipelines that build and deploy the tools and services in
my homelab.

## Pipelines

| File | What it does | Agent |
| --- | --- | --- |
| `pipeline_docker_images_deploy.groovy` | Builds all images from the `docker_images` repo with `docker buildx bake`, pushes them to the private registry and recreates the service containers | `ryzen9` |
| `pipeline_tools_build_deploy.groovy` | Builds UPX, GoReleaser and Trivy from source (x86-64-v3, PGO) and installs them on the agent | `ryzen9` |
| `pipeline_navidrome_build_deploy.groovy` | Builds Navidrome from `master` and copies the binary to the router | `ryzen9` |
| `pipeline_feishin_build_deploy.groovy` | Builds Feishin installers for Linux and Windows and publishes them to a web server, keeping the last five builds | `ryzen9` |
| `pipeline_jenkins_plugins_update.groovy` | Pauses other jobs, waits for running builds, installs plugin updates and safe-restarts Jenkins | `ALDERLAKE` |

## Requirements

- Plugins: Pipeline, Git, Timestamper, Workspace Cleanup, Credentials Binding.
- Credentials: `gitea_jenkins_api_token` (secret text) and
  `docker_registry_digtvbg_5000_credentials` (username and password).
- The plugin update job calls the Jenkins API directly, so it has to run
  unsandboxed (or with the matching script approvals).
- Build agents need Docker with Buildx, Go, Node.js with pnpm, GNU make,
  CMake 3.13 or newer and a C++ compiler (UPX build), UPX, rsync, Git 2.31 or
  newer and SSH access to the deployment hosts.
- The plugin update job skips updates that need a newer Jenkins core and fails
  when a plugin or one of its dependencies cannot be installed.
- The tools job installs into `/usr/local/bin` with `sudo`, so the agent user
  needs passwordless sudo for `install`.

## License

GPL-3.0, see [LICENSE](LICENSE).
