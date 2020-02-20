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

@Stage(name = "get-version", buildTool = ["npm"], type = [ProjectType.APPLICATION, ProjectType.LIBRARY])
class GetVersionNpmApplicationLibrary {
    Script script

    void run(context) {
        script.dir("${context.workDir}") {
            //context.codebase.startFrom = "9.9.1"
            //context.codebase.build_number = "0"
            //context.codebase.versioning_type = "edp"
            def startFrom = "9.9.1"
            def build_number = "0"
            def versioning_type = "edp"

            if (versioning_type == "edp") {
                context.codebase.version = script.sh(
                script: """
                    sed -i "/version/c\\  \\"version\\": \\"${startFrom}-${build_number}\\"," package.json
                    echo "${startFrom}-${build_number}"
                """, returnStdout: true
                ).trim().toLowerCase()
                context.codebase.buildVersion = "${context.codebase.version}"
            } else {
                context.codebase.version = script.sh(
                script: """
                    node -p "require('./package.json').version"
                """, returnStdout: true
                ).trim().toLowerCase()
                context.codebase.buildVersion = "${context.codebase.version}-${script.BUILD_NUMBER}"
            }
        }
        context.job.setDisplayName("${script.currentBuild.number}-${context.git.branch}-${context.codebase.version}")
        context.codebase.deployableModuleDir = "${context.workDir}"
        script.println("[JENKINS][DEBUG] Application version - ${context.codebase.version}")
    }
}
