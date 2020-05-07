/* Copyright 2020 EPAM Systems.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

See the License for the specific language governing permissions and
limitations under the License.*/

package com.epam.edp.stages.impl.ci.impl.commitvalidate


import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "commit-validate", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class CommitValidate {
    Script script

    def getLastCommitMessage() {
        return script.sh(
                script: "git log -1 --pretty=%B",
                returnStdout: true
        ).trim()
    }

    def run(context) {
        script.println("[JENKINS][DEBUG] Start CommitValidate")
        def pattern = context.codebase.config.commitMessagePattern
        script.println("[JENKINS][INFO] Pattern to validate commit message: ${pattern}")
//        def pattern = '^\\[EPMDEDP-\\d{4}\\]:.*$'
        script.dir("${context.workDir}") {
            def msg = getLastCommitMessage()
            script.println("[JENKINS][INFO] Commit message to validate has been fetched:\n ${msg}")
            def valid = (msg ==~ /(?s)${pattern}/)

            script.println("[JENKINS][INFO] VALID: ${valid}")
            if (!valid) {
                script.println("[JENKINS][INFO] Commit message is invalid")
            } else {
                script.println("[JENKINS][INFO] Commit message is valid")
            }
        }
        script.println("[JENKINS][DEBUG] END CommitValidate")
    }
}
