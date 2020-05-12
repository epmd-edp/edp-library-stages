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

package com.epam.edp.stages.impl.ci.impl.jirafixversion

import com.epam.edp.stages.impl.ci.ProjectType
import com.epam.edp.stages.impl.ci.Stage

@Stage(name = "jira-fix-version", buildTool = ["maven", "npm", "dotnet", "gradle", "any"], type = [ProjectType.APPLICATION, ProjectType.AUTOTESTS, ProjectType.LIBRARY])
class JiraFixVersion {
    Script script

    def getLastSuccessfulCommit() {
        def lastSuccessfulBuild = script.currentBuild.rawBuild.getPreviousSuccessfulBuild()
        script.println("[JENKINS][DEBUG] --------------------------------------------------- ${lastSuccessfulBuild}")
        if (lastSuccessfulBuild) {
            script.println("[JENKINS][DEBUG] inside if")
            return commitHashForBuild(lastSuccessfulBuild)
        }
        script.println("[JENKINS][DEBUG] outside if")
        return null
    }

    @NonCPS
    def commitHashForBuild(build) {
        script.println("[JENKINS][DEBUG] commitHashForBuild")

        script.println("[JENKINS][DEBUG] build ${build}")
        script.println("[JENKINS][DEBUG] build?.actions ${build?.actions}")
        script.println("[JENKINS][DEBUG] build.actions ${build.actions}")
        def scmAction = build?.actions.forEach { s -> println s }
//        script.println("[JENKINS][DEBUG] scmAction ${scmAction}")
        return scmAction?.revision?.hash
    }

    void run(context) {
        script.println("[JENKINS][DEBUG] ---------------------------------------------------")
        def res = getLastSuccessfulCommit()
        script.println("[JENKINS][DEBUG] RESULT ${res}")
    }
}
