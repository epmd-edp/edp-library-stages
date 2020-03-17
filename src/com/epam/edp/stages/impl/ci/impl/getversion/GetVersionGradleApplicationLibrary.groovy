/* Copyright 2019 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.getversion


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "get-version", buildTool = ["gradle"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionGradleApplicationLibrary {
    Script script
    def setVersionToArtifact(buildNumber, branchVersion, context) {
       def newBuildNumber = ++buildNumber
       script.sh """
             set -eo pipefail
             sed -i "s/version = ".*"/version = \\'${branchVersion}-${newBuildNumber}\\'/" build.gradle
             kubectl patch codebasebranches.v2.edp.epam.com ${context.codebase.config.name}-${context.git.branch} --type=merge -p '{\"spec\": {\"build\": "${newBuildNumber}"}}'
        """

       return "${branchVersion}.${newBuildNumber}"
    }

    void run(context) {
        script.dir("${context.workDir}") {
            script.withCredentials([script.usernamePassword(credentialsId: "${context.nexus.credentialsId}",
                    passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                if (context.codebase.config.versioningType == "edp") {
                    def branchIndex = context.codebase.config.codebase_branch.branchName.findIndexOf{it == context.git.branch}
                    def build = context.codebase.config.codebase_branch.build_number.get(branchIndex).toInteger()
                    def version = context.codebase.config.codebase_branch.version.get(branchIndex)

                    context.codebase.version = setVersionToArtifact(build, version, context)
                    context.codebase.buildVersion = context.codebase.version
                } else {
                    context.codebase.version = script.sh(
                            script: """
                            set +x
                            ${context.buildTool.command} -PnexusLogin=${script.USERNAME} -PnexusPassword=${script.PASSWORD} properties -q | grep "version:" | awk '{print \$2}'
                        """,
                            returnStdout: true
                    ).trim().toLowerCase()
                    context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
                 }
            }
            context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
            context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
            context.codebase.deployableModuleDir = "${context.workDir}/build/libs"
        }
        script.println("[JENKINS][DEBUG] Artifact version - ${context.codebase.version}")
    }
}
