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

    def getLastSuccessfulCommitId() {
        def lastSuccessfulBuild = script.currentBuild.rawBuild.getPreviousSuccessfulBuild()
        return lastSuccessfulBuild ? commitHashForBuild(lastSuccessfulBuild) : null
    }

    def getCurrentCommitId(workDir) {
        script.dir("${workDir}") {
            return script.sh(
                    script: "git log --format=%H -n 1",
                    returnStdout: true
            ).trim()
        }
    }

    def getCommitsInRange(workDir, start, end) {
        script.dir("${workDir}") {
            return script.sh(
                    script: "git rev-list ${start} ${end}",
                    returnStdout: true
            ).trim()
        }
    }

    def commitHashForBuild(build) {
        return build.getAction(hudson.plugins.git.util.BuildData.class).lastBuiltRevision.sha1String
    }

    void run(context) {
        def currentCommitId = getCurrentCommitId(context.workDir)
        script.println("[JENKINS][DEBUG] Current commit id ${currentCommitId}")
        def lastCommitId = getLastSuccessfulCommitId()
        script.println("[JENKINS][DEBUG] Last successfull commit id ${lastCommitId}")
        def commits = getCommitsInRange(context.workDir, lastCommitId, currentCommitId)
        script.println("[JENKINS][DEBUG] Commit id's in range ${commits}")
    }
}

//git rev-list 7e484aaa9894e67daccdad9a487740ca80319125 49254920508b88fbe33a9a8377c4cf234a995971
//$ git log --format=%B -n 1 426e730807208f6db8ea0bfc7419a86800139a7f