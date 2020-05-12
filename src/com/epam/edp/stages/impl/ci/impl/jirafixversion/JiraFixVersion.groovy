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
        return lastSuccessfulBuild != null ? commitHashForBuild(lastSuccessfulBuild) : null
    }

    def commitHashForBuild(build) {
        def scmAction = build?.actions.find { action -> action instanceof jenkins.scm.api.SCMRevisionAction }
        return scmAction?.revision?.hash
    }

    void run(context) {
        script.println("[JENKINS][DEBUG] ---------------------------------------------------")
        def res = getLastSuccessfulCommit()
        script.println("[JENKINS][DEBUG] RESULT ${res}")
    }
}
